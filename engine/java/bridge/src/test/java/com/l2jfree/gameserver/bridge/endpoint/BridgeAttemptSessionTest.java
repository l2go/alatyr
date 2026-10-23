package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BridgeAttemptSessionTest
{
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
