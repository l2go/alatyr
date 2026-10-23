/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.SemanticFacts;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.bridge.ClientSink;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeMessageArg;
import com.l2jfree.gameserver.network.bridge.BridgeValue;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.bridge.FactSink;
import com.l2jfree.gameserver.network.bridge.SemanticPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Outbound side of one bridge client. World packets are not written as server frames. A teleport completion is queued
 * on the client and is not run on the caller, which may be holding the teleport lock. The bye is a bridge record on
 * the listener socket, not a close packet on the virtual channel.
 */
public final class BridgeSink implements ClientSink
{
	private final Consumer<List<Envelope>> _outbound;
	private final AtomicBoolean _disconnected = new AtomicBoolean();
	private L2Client _client;
	private final long _born = System.nanoTime();
	private long _sequence;
	private boolean _failed;
	private final Map<Integer, ActorView> _actors = new HashMap<>();

	private record ActorView(Map<Integer, BridgeField> fields, Long run, Long walk) {}
	
	public BridgeSink(Consumer<List<Envelope>> outbound)
	{
		_outbound = outbound;
	}
	
	/** Attaches the client after {@link L2Client#forBridge} returns. */
	public void attach(L2Client client)
	{
		if (client == null || !client.isBridge())
			throw new IllegalArgumentException("client");
		
		_client = client;
	}
	
	@Override
	public boolean isBridge()
	{
		return true;
	}
	
	@Override
	public synchronized void deliver(L2ServerPacket packet, L2Player player)
	{
		if (_failed || _disconnected.get() || _client == null || _client.isBridgeDisconnectRequested())
			return;
		try
		{
			BridgePacketRegistry.Kind kind = BridgePacketRegistry.classify(packet.getClass());
			if (kind == BridgePacketRegistry.Kind.NONESSENTIAL)
				return;
			if (kind == BridgePacketRegistry.Kind.FATAL)
				throw new IllegalArgumentException("unregistered packet class");
			Stage stage = new Stage(player);
			((SemanticPacket)packet).describe(stage, player);
			var records = new ArrayList<Envelope>();
			long sequence = _sequence;
			for (BridgeFact fact : stage.facts)
				records.add(SemanticFacts.envelope(fact, sequence = Math.incrementExact(sequence),
						stage.ms, 1, domain(fact.kind())));
			// Validate every observation before any delivery or receipt becomes visible.
			Codec codec = Codec.bridge();
			for (Envelope record : records)
				codec.encode(List.of(record));
			stage.validateActors();
			if (_outbound != null)
				for (Envelope record : records)
					_outbound.accept(List.of(record));
			_sequence = sequence;
			stage.commitActors();
			if (player != null && _client.getActiveChar() == player)
			{
				for (BridgeFact fact : stage.facts)
					if (fact.kind() == 11)
						EffectLedger.spoken(_client, player, rawId(fact));
				for (int id : stage.messages)
					EffectLedger.systemMessage(_client, player, id);
			}
		}
		catch (RuntimeException failure)
		{
			_failed = true;
			EffectLedger.incomplete(player);
			_client.requestBridgeDisconnect(BridgeFailure.internal());
		}
	}

	private static int rawId(BridgeFact fact)
	{
		for (BridgeField field : fact.fields())
			if (field.number() == 64)
				return Math.toIntExact(((BridgeValue.Number)field.value()).value());
		return 0;
	}

	private static String domain(int kind)
	{
		if (kind <= 10 || kind == 24)
			return "objects";
		return switch (kind)
		{
			case 11, 18 -> "dialogue";
			case 12, 13 -> "trade";
			case 14, 15 -> "inventory";
			case 16, 17 -> "self";
			case 19 -> "history";
			case 20, 21 -> "quests";
			default -> "skills";
		};
	}

	/** Packet-local staging keeps malformed windows from leaking partial actions. */
	private final class Stage implements FactSink
	{
		final long ms = Math.max(0, (System.nanoTime() - _born) / 1_000_000);
		final L2Player player;
		final List<BridgeFact> facts = new ArrayList<>();
		final List<Integer> messages = new ArrayList<>();
		final Map<Integer, ActorView> changes = new HashMap<>();

		Stage(L2Player player) { this.player = player; }

		@Override
		public long now() { return ms; }

		@Override
		public void emit(BridgeFact fact)
		{
			if (facts.size() >= 256)
				throw new IllegalArgumentException("too many packet observations");
			facts.add(fact);
			int id = rawId(fact);
			if (id == 0)
				return;
			if (fact.kind() == 10)
			{
				changes.put(id, null);
				return;
			}
			ActorView previous = view(id);
			if (fact.kind() != 1 && previous == null)
				return;
			if (fact.kind() != 1 && (fact.kind() < 2 || fact.kind() > 4))
				return;
			var fields = new HashMap<Integer, BridgeField>();
			if (previous != null)
				fields.putAll(previous.fields());
			for (BridgeField field : fact.fields())
				if (field.number() == 64 || field.number() == 11 || field.number() == 12 || field.number() == 13
						|| field.number() == 15 || field.number() == 19 || field.number() == 70 || field.number() == 73)
					fields.put(field.number(), field);
			changes.put(id, new ActorView(Map.copyOf(fields), previous == null ? null : previous.run(),
					previous == null ? null : previous.walk()));
		}

		private ActorView view(int id)
		{
			return changes.containsKey(id) ? changes.get(id) : _actors.get(id);
		}

		@Override
		public void movementSpeeds(int id, long run, long walk)
		{
			ActorView old = view(id);
			changes.put(id, new ActorView(old == null ? Map.of() : old.fields(), run, walk));
		}

		@Override
		public void changeMoveType(int id, boolean running)
		{
			ActorView old = view(id);
			var fields = new HashMap<Integer, BridgeField>();
			if (old != null)
				fields.putAll(old.fields());
			fields.put(64, BridgeField.number(64, id));
			Long speed = old == null ? null : running ? old.run() : old.walk();
			if (speed == null)
				fields.remove(15);
			else
				fields.put(15, BridgeField.number(15, speed));
			emit(new BridgeFact(1, List.copyOf(fields.values())));
		}

		@Override
		public void systemMessage(int id, List<BridgeMessageArg> args)
		{
			boolean own = player != null && _client.getActiveChar() == player;
			for (BridgeFact fact : BridgeMessages.describe(id, List.copyOf(args), own, own ? player.getObjectId() : 0))
				emit(fact);
			messages.add(id);
		}

		void validateActors()
		{
			int count = _actors.size();
			for (var change : changes.entrySet())
			{
				if (change.getValue() == null && _actors.containsKey(change.getKey()))
					count--;
				else if (change.getValue() != null && !_actors.containsKey(change.getKey()))
					count++;
			}
			if (count > 4096)
				throw new IllegalArgumentException("too many observed actors");
		}

		void commitActors()
		{
			for (var change : changes.entrySet())
				if (change.getValue() == null)
					_actors.remove(change.getKey());
				else
					_actors.put(change.getKey(), change.getValue());
		}
	}
	
	@Override
	public void disconnect(BridgeFailure failure)
	{
		if (failure == null || _outbound == null || !_disconnected.compareAndSet(false, true))
			return;
		
		_outbound.accept(List.of(BridgeFrames.bye(failure.byeReason())));
	}
	
	@Override
	public void ownTeleported(long generation)
	{
		L2Client client = _client;
		if (client == null)
			return;
		
		client.submit(() ->
		{
			L2Player player = client.getActiveChar();
			if (player != null)
				player.completeBridgeTeleport(generation);
		});
	}
}
