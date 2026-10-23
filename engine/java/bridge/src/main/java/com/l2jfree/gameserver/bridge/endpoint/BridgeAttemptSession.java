/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.network.L2Client;

/** Registers on the reader, executes in FIFO, and retains the first response before publishing it. */
final class BridgeAttemptSession
{
	private final BridgeIntents intents = new BridgeIntents();
	private final Consumer<Runnable> submit;
	private Consumer<byte[]> outbound;
	private record Queued(byte[] refusal, Fence fence) {}
	/** A reader invalidates the source fence before any queued task can begin. No callback or lock is needed. */
	static final class Fence { volatile boolean valid = true; volatile boolean ready = true; }
	private final java.util.Map<Long, Queued> queued = new java.util.HashMap<>();
	private boolean detached;
	private volatile long epoch = 1;
	private Fence activeFence;

	BridgeAttemptSession(Consumer<Runnable> submit, Consumer<byte[]> outbound)
	{
		this.submit = submit;
		this.outbound = outbound;
	}

	void ready()
	{
		intents.markReady();
	}

	synchronized void ready(Fence fence)
	{
		if (!detached && activeFence == fence && fence.valid)
		{
			fence.ready = true;
			intents.markReady();
		}
	}

	synchronized void bindFence(Fence fence)
	{
		activeFence = fence;
	}

	void checkpoint()
	{
		intents.markNotReady();
	}

	java.util.List<byte[]> retainedResults()
	{
		return intents.retainedResults();
	}

	/** Reader-side cancellation; running work is absent from queued and keeps its one terminal outcome. */
	synchronized void detach()
	{
		detached = true;
		intents.markNotReady();
		for (var entry : queued.entrySet())
			intents.complete(entry.getKey(), entry.getValue().refusal());
		queued.clear();
	}

	/** Rebinds output without running any work. The checkpoint owner publishes this copy before admission opens. */
	synchronized java.util.List<byte[]> resume(long nextEpoch, Consumer<byte[]> output)
	{
		epoch = nextEpoch;
		outbound = java.util.Objects.requireNonNull(output);
		detached = false;
		intents.markNotReady();
		return intents.retainedResults();
	}

	synchronized void resume(long nextEpoch, Consumer<byte[]> output, Fence fence)
	{
		resume(nextEpoch, output);
		activeFence = fence;
	}

	BridgeIntents.Decision accept(L2Client client, Envelope intent)
	{
		return accept(client, intent, null);
	}

	BridgeIntents.Decision accept(L2Client client, Envelope intent, Fence fence)
	{
		long n = intent.field(1).value().u();
		long verb = intent.field(2).value().u();
		if (intent.unsupported())
		{
			if (intent.encodedBytes() > 4096)
				throw com.l2jfree.gameserver.bridge.wire.WireException.frame();
			byte[] identity = intent.unsupportedHash().clone();
			return accept(n, identity, () -> BridgeResults.encode(n, verb, 3, 9, null),
					BridgeResults.encode(n, verb, 3, 1, null), BridgeResults.transportLost(n, verb), fence);
		}
		long received = System.nanoTime();
		long ttl = intent.field(4) == null ? 5000 : intent.field(4).value().u();
		var fields = new java.util.ArrayList<>(intent.fields());
		for (int i = 0; i < fields.size(); i++)
		{
			var field = fields.get(i);
			if (field.number() == 18)
			{
				var skill = new java.util.ArrayList<>(field.value().fields());
				skill.sort(java.util.Comparator.comparingInt(com.l2jfree.gameserver.bridge.wire.Field::number));
				fields.set(i, com.l2jfree.gameserver.bridge.wire.Field.obj(18, field.essential(), skill));
			}
		}
		fields.sort(java.util.Comparator.comparingInt(com.l2jfree.gameserver.bridge.wire.Field::number));
		byte[] normalized = Codec.bridge().encode(java.util.List.of(Envelope.of(intent.type(), fields)));
		if (normalized.length > 4096)
			throw com.l2jfree.gameserver.bridge.wire.WireException.frame();
		// Decode our own canonical bytes to sever all receive-side mutable arrays before FIFO submission.
		Envelope acceptedIntent = Codec.bridge().decode(normalized).get(0);
		return accept(n, normalized, () -> execute(client, acceptedIntent, n, verb, received, ttl, fence),
				BridgeResults.encode(n, verb, 3, 1, null), BridgeResults.transportLost(n, verb), fence);
	}

	private byte[] execute(L2Client client, Envelope intent, long n, long verb, long received, long ttl, Fence fence)
		{
			if (client.isBridgeDisconnectRequested())
				return BridgeResults.encode(n, verb, 3, 16, null);
			if (client.getState() != L2Client.GameClientState.IN_GAME
					|| client.getActiveChar() == null)
				return BridgeResults.encode(n, verb, 3, 1, null);
			if (intent.field(3).value().u() != epoch)
				return BridgeResults.encode(n, verb, 3, 1, null);
			if (ttl < 0 || ttl > 5000)
				return BridgeResults.encode(n, verb, 3, 10, null);
			if (System.nanoTime() - received >= ttl * 1_000_000L)
				return BridgeResults.encode(n, verb, 3, 6, null);
			boolean started = false;
			try
			{
				com.l2jfree.gameserver.model.items.L2ItemInstance ground = null;
				if (verb == 4)
				{
					var object = com.l2jfree.gameserver.model.world.L2World.getInstance().findObject(BridgeCommand.positive(intent, 64));
					if (!(object instanceof com.l2jfree.gameserver.model.items.L2ItemInstance item) || !item.isVisible())
						throw new IllegalArgumentException("ground item");
					ground = item;
				}
				var packet = BridgeCommand.packet(client, intent);
				int opcode = switch ((int)verb)
				{
					case 1 -> 0x0f;
					case 2, 4, 5, 10 -> 0x1f;
					case 3 -> 0x01;
					case 6 -> 0x21;
					case 7 -> 0x40;
					case 8 -> 0x1e;
					case 9 -> 0x6d;
					case 12 -> 0x39;
					case 13 -> 0x6c;
					default -> throw new IllegalArgumentException("verb");
				};
				if (!com.l2jfree.gameserver.network.L2ClientSelectorThread.getInstance().canReceivePacketFrom(client, opcode))
					return BridgeResults.encode(n, verb, 3, 7, null);
				final var source = ground;
				final int takenItem = source == null ? 0 : source.getItemId();
				final long takenCount = source == null ? 0 : source.getCount();
				started = true;
				var attempt = BridgeExecution.run(client, packet, active ->
				{
					if (source == null || BridgeOutcome.match(active, 4, takenItem, source.getObjectId(), takenCount) == BridgeOutcome.DONE)
						return;
					if (fence != null && !fence.valid || intent.field(3).value().u() != epoch
							|| client.isBridgeDisconnectRequested())
					{
						active.markInterrupted();
						return;
					}
					if (client.isBridgeDisconnectRequested() || client.getState() != L2Client.GameClientState.IN_GAME
							|| com.l2jfree.gameserver.model.world.L2World.getInstance().findObject(source.getObjectId()) != source
							|| !source.isVisible())
						return;
					if (!com.l2jfree.gameserver.network.L2ClientSelectorThread.getInstance().canReceivePacketFrom(client, 0x1f))
						return;
					var second = com.l2jfree.gameserver.network.packets.client.Action.forBridge(source.getObjectId());
					second.bindDirect(client);
					second.run();
					if (second.failed())
						active.markFailed();
				});
				int stage = BridgeOutcome.match(attempt, Math.toIntExact(verb),
						verb == 4 ? takenItem : intent.field(66) == null ? 0 : BridgeCommand.integer(intent, 66),
						intent.field(64) == null ? 0 : BridgeCommand.integer(intent, 64),
						verb == 4 ? takenCount : intent.field(16) == null ? 0 : intent.field(16).value().s());
				if (verb == 13)
				{
					var skill = intent.field(18).value().fields();
					int id = Math.toIntExact(skill.stream().filter(field -> field.number() == 1).findFirst().orElseThrow().value().s());
					int level = Math.toIntExact(skill.stream().filter(field -> field.number() == 2).findFirst().orElseThrow().value().s());
					stage = BridgeOutcome.learn(attempt, id, level);
				}
				return BridgeResults.encode(n, verb, stage, attempt.isFailed() ? 24 : 0, attempt);
			}
			catch (IllegalArgumentException | ArithmeticException invalid)
			{
				return BridgeResults.encode(n, verb, started ? 4 : 3, started ? 24 : 10, null);
			}
			catch (RuntimeException failure)
			{
				return BridgeResults.encode(n, verb, 4, 24, null);
			}
		}

	BridgeIntents.Decision accept(long n, byte[] normalized, Supplier<byte[]> execution)
	{
		return accept(n, normalized, execution, BridgeResults.encode(n, 0, 3, 1, null),
				BridgeResults.transportLost(n, 0), null);
	}

	private BridgeIntents.Decision accept(long n, byte[] normalized, Supplier<byte[]> execution, byte[] notReady,
			byte[] transportLost, Fence fence)
	{
		BridgeIntents.Decision decision;
		byte[] immediate = null;
		Consumer<byte[]> target;
		synchronized (this)
		{
			// Recheck under the admission monitor: EOF or replacement may win after the reader's outer check.
			// A stale reader is ignored, never registered against the replacement epoch or thrown into the selector.
			if (detached || fence != null && (!fence.valid || activeFence != fence))
				return null;
			if (fence != null && !fence.ready)
				intents.markNotReady();
			decision = intents.offer(n, normalized);
			if (decision.kind == BridgeIntents.Kind.NOT_READY)
			{
				intents.complete(n, notReady);
				immediate = intents.result(n);
			}
			else if (decision.kind == BridgeIntents.Kind.REPLAY)
				immediate = intents.result(n);
			else if (decision.kind == BridgeIntents.Kind.UNRECOVERABLE)
				immediate = BridgeResults.encode(n, 0, 5, 20, null);
			else if (decision.kind == BridgeIntents.Kind.ACCEPTED)
				queued.put(n, new Queued(transportLost, fence));
			target = outbound;
		}
		if (immediate != null)
			target.accept(immediate);
		if (decision.kind == BridgeIntents.Kind.ACCEPTED)
		{
			submit.accept(() ->
			{
				boolean canceled;
				boolean unavailable;
				synchronized (BridgeAttemptSession.this)
				{
					Queued entry = queued.remove(n);
					if (entry == null)
						return;
					canceled = entry.fence() != null && !entry.fence().valid;
					unavailable = entry.fence() != null && !entry.fence().ready;
				}
				byte[] result = canceled ? transportLost : unavailable ? notReady : execution.get();
				Consumer<byte[]> destination;
				byte[] retained;
				synchronized (BridgeAttemptSession.this)
				{
					intents.complete(n, result);
					retained = intents.result(n);
					destination = detached ? null : outbound;
				}
				if (destination != null && retained != null)
					destination.accept(retained);
			});
		}
		return decision;
	}
}
