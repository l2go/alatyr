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
	private final Consumer<byte[]> outbound;

	BridgeAttemptSession(Consumer<Runnable> submit, Consumer<byte[]> outbound)
	{
		this.submit = submit;
		this.outbound = outbound;
	}

	void ready()
	{
		intents.markReady();
	}

	BridgeIntents.Decision accept(L2Client client, Envelope intent)
	{
		long n = intent.field(1).value().u();
		long verb = intent.field(2).value().u();
		return accept(n, Codec.bridge().encode(java.util.List.of(intent)), () ->
		{
			if (client.isBridgeDisconnectRequested() || client.getState() != L2Client.GameClientState.IN_GAME
					|| client.getActiveChar() == null)
				return BridgeResults.encode(n, verb, 3, 1, null);
			if (intent.field(3).value().u() != 1)
				return BridgeResults.encode(n, verb, 3, 1, null);
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
				var attempt = BridgeExecution.run(client, packet, active ->
				{
					if (source == null || BridgeOutcome.match(active, 4, takenItem, source.getObjectId(), takenCount) == BridgeOutcome.DONE)
						return;
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
				return BridgeResults.encode(n, verb, 3, 10, null);
			}
		});
	}

	BridgeIntents.Decision accept(long n, byte[] normalized, Supplier<byte[]> execution)
	{
		BridgeIntents.Decision decision = intents.offer(n, normalized);
		if (decision.kind == BridgeIntents.Kind.REPLAY)
		{
			byte[] result = intents.result(n);
			if (result != null)
				outbound.accept(result);
		}
		else if (decision.kind == BridgeIntents.Kind.ACCEPTED)
		{
			submit.accept(() ->
			{
				byte[] result = execution.get();
				intents.complete(n, result);
				outbound.accept(intents.result(n));
			});
		}
		return decision;
	}
}
