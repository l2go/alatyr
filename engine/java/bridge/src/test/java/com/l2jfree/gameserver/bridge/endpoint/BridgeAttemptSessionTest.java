package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BridgeAttemptSessionTest
{
	@Test
	void reorderedFieldsReplayTheSamePendingAttempt()
	{
		var client = org.mockito.Mockito.mock(com.l2jfree.gameserver.network.L2Client.class);
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, bytes -> { });
		session.ready();
		var n = com.l2jfree.gameserver.bridge.wire.Field.u(1, true, 1);
		var verb = com.l2jfree.gameserver.bridge.wire.Field.u(2, true, 9);
		var control = com.l2jfree.gameserver.bridge.wire.Field.u(3, true, 1);
		var first = com.l2jfree.gameserver.bridge.wire.Envelope.of(
				com.l2jfree.gameserver.bridge.wire.Schemas.INTENT, java.util.List.of(n, verb, control));
		var reordered = com.l2jfree.gameserver.bridge.wire.Envelope.of(
				com.l2jfree.gameserver.bridge.wire.Schemas.INTENT, java.util.List.of(control, n, verb));
		assertThat(session.accept(client, first).kind).isEqualTo(BridgeIntents.Kind.ACCEPTED);
		assertThat(session.accept(client, reordered).kind).isEqualTo(BridgeIntents.Kind.REPLAY);
		assertThat(fifo).hasSize(1);
	}

	@Test
	void connectedAndAuthenticatedClientsNeverBindAnyVerb()
	{
		for (var state : new com.l2jfree.gameserver.network.L2Client.GameClientState[] {
				com.l2jfree.gameserver.network.L2Client.GameClientState.CONNECTED,
				com.l2jfree.gameserver.network.L2Client.GameClientState.AUTHED })
		{
			var client = org.mockito.Mockito.mock(com.l2jfree.gameserver.network.L2Client.class);
			org.mockito.Mockito.when(client.getState()).thenReturn(state);
			for (int verb : new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13 })
			{
				ArrayDeque<Runnable> fifo = new ArrayDeque<>();
				ArrayList<byte[]> output = new ArrayList<>();
				BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, output::add);
				session.ready();
				var intent = com.l2jfree.gameserver.bridge.wire.Envelope.of(
						com.l2jfree.gameserver.bridge.wire.Schemas.INTENT, java.util.List.of(
						com.l2jfree.gameserver.bridge.wire.Field.u(1, true, 1),
						com.l2jfree.gameserver.bridge.wire.Field.u(2, true, verb),
						com.l2jfree.gameserver.bridge.wire.Field.u(3, true, 1)));
				session.accept(client, intent);
				fifo.remove().run();
				var result = com.l2jfree.gameserver.bridge.wire.Codec.bridge().decode(output.get(0)).get(0);
				assertThat(result.field(2).value().u()).isEqualTo(3);
				assertThat(result.field(3).value().u()).isEqualTo(1);
				org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).getActiveChar();
			}
		}
	}

	@Test
	void everyVerbBeforeReadySchedulesNoWorldWork()
	{
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, bytes -> { throw new AssertionError("output"); });
		for (int verb : new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13 })
			assertThat(session.accept(1, new byte[] { (byte)verb }, () -> { throw new AssertionError("world"); }).kind)
					.isEqualTo(BridgeIntents.Kind.NOT_READY);
		assertThat(fifo).isEmpty();
	}

	@Test
	void aLivePurchaseRunsTheDirectHandlerOnceAndReplaysItsEncodedReceipt()
	{
		var player = BridgeTestPlayers.player();
		var client = org.mockito.Mockito.mock(com.l2jfree.gameserver.network.L2Client.class);
		org.mockito.Mockito.when(client.isBridge()).thenReturn(true);
		org.mockito.Mockito.when(client.getActiveChar()).thenReturn(player);
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		ArrayList<byte[]> delivered = new ArrayList<>();
		AtomicInteger purchases = new AtomicInteger();
		BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, delivered::add);
		session.ready();
		java.util.function.Supplier<byte[]> purchase = () ->
		{
			var packet = new com.l2jfree.gameserver.network.packets.L2ClientPacket()
			{
				@Override
				protected void readImpl() { throw new AssertionError("direct read"); }
				@Override
				protected void runImpl()
				{
					purchases.incrementAndGet();
					com.l2jfree.gameserver.network.bridge.EffectLedger.record(player,
							com.l2jfree.gameserver.network.bridge.BridgeEffect.item(1060, 70, 2));
					com.l2jfree.gameserver.network.bridge.EffectLedger.record(player,
							com.l2jfree.gameserver.network.bridge.BridgeEffect.coin(-13));
				}
				@Override
				public String getType() { return "purchase fixture"; }
			};
			var attempt = BridgeExecution.run(client, packet);
			return BridgeResults.encode(1, 7, BridgeOutcome.match(attempt, 7, 1060, 0, 2), 0, attempt);
		};
		session.accept(1, new byte[] { 7, 2 }, purchase);
		session.accept(1, new byte[] { 7, 2 }, purchase);
		fifo.remove().run();
		session.accept(1, new byte[] { 7, 2 }, purchase);
		assertThat(purchases).hasValue(1);
		assertThat(delivered).hasSize(2);
		assertThat(delivered.get(1)).containsExactly(delivered.get(0));
		var result = com.l2jfree.gameserver.bridge.wire.Codec.bridge().decode(delivered.get(0)).get(0);
		assertThat(result.field(2).value().u()).isEqualTo(2);
		assertThat(result.field(66).value().items()).hasSize(2);
	}

	@Test
	void liveDuplicatesExecuteOnceAndReplayTheFirstImmutableResult()
	{
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		ArrayList<byte[]> delivered = new ArrayList<>();
		AtomicInteger purchases = new AtomicInteger();
		BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, delivered::add);
		session.ready();
		session.accept(1, new byte[] { 7, 2 }, () ->
		{
			purchases.incrementAndGet();
			return new byte[] { 2, 13 };
		});
		session.accept(1, new byte[] { 7, 2 }, () -> { throw new AssertionError("duplicate execution"); });
		assertThat(fifo).hasSize(1);
		fifo.remove().run();
		delivered.get(0)[0] = 99;
		session.accept(1, new byte[] { 7, 2 }, () -> { throw new AssertionError("duplicate execution"); });
		assertThat(purchases).hasValue(1);
		assertThat(fifo).isEmpty();
		assertThat(delivered.get(1)).containsExactly(2, 13);
		assertThat(session.accept(1, new byte[] { 7, 3 }, () -> null).kind).isEqualTo(BridgeIntents.Kind.MISMATCH);
		assertThat(delivered).hasSize(2);
	}
}
