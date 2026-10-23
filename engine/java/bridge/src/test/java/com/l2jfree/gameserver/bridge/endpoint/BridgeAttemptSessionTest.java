package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BridgeAttemptSessionTest
{
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
