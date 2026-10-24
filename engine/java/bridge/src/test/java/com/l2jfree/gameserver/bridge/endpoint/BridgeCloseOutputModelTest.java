/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import com.l2jfree.gameserver.bridge.wire.Codec;

/** Bounded event exploration of the production output rules, with no wall-clock waits. */
class BridgeCloseOutputModelTest
{
	private enum Event { TRUE, FALSE, THROW, BEGIN, END, DEADLINE, EOF1, EOF2, CLOSE1, CLOSE2,
		SHUTDOWN, REPEAT, RELEASE, CHECKPOINT, FACT, BUDGET_END, FRAME2, ADVANCE_SAVE_CLOCK }
	private static final byte[] FULL = new byte[BridgeLoopback.OUT_QUEUE_BYTES];
	private static final byte[] OLD = { 1 };
	private static final byte[] SNAPSHOT = { 2 };
	private static final byte[] FACT = { 3 };
	private static final BridgeCheckpointOutput PRODUCER = mock(BridgeCheckpointOutput.class);
	private static final BridgeLoopback LOOP = mock(BridgeLoopback.class);

	@Test
	void enumerateCloseOutputDeadlineShutdownAndCheckpointOrders()
	{
		Set<String> visited = new HashSet<>();
		for (boolean full : new boolean[] { false, true })
			explore(new ArrayList<>(), 0, full, visited);
		assertTrue(visited.size() > 1000, "The event cross-product must actually be explored");
	}

	private static void explore(List<Event> trace, int used, boolean full, Set<String> visited)
	{
		State state = replay(trace, full);
		String key = full + ":" + used + ":" + state.key();
		if (!visited.add(key)) return;
		state.check(trace);
		State settled = replay(trace, full);
		settled.drain(trace);
		if (trace.size() == 6) return;
		for (Event event : Event.values())
		{
			int bit = 1 << event.ordinal();
			if ((used & bit) != 0 || !state.admits(event)) continue;
			trace.add(event);
			explore(trace, used | bit, full, visited);
			trace.remove(trace.size() - 1);
		}
	}

	private static State replay(List<Event> trace, boolean full)
	{
		State state = new State(full);
		for (Event event : trace) state.apply(event);
		return state;
	}

	private static final class State
	{
		final BridgeLoopback.Transport[] sockets = { new BridgeLoopback.Transport(new byte[32]),
				new BridgeLoopback.Transport(new byte[32]) };
		final BridgeShutdownBudget budget = new BridgeShutdownBudget();
		final BridgeLoopback.Session owner = LOOP.new Session(sockets[0]);
		long now = 1, firstBudget;
		int outcome, recipient = -1, preferred = 1, writer = -1, byes, lastSeq;
		boolean released, captured, snapshotPending, stopped, lateSuccess, notified;

		State(boolean full)
		{
			owner.sessionTransports.add(sockets[1]);
			owner.saveDeadline = 10;
			owner.closeReplyTransport = sockets[1];
			for (var t : sockets) { t.awaitingResult = true; t.resultDeadline = 10; }
			if (full) { sockets[1].queued.add(FULL); sockets[1].held = FULL.length; }
		}

		boolean admits(Event e)
		{
			return switch (e)
			{
				case TRUE, FALSE, THROW -> !notified;
				case BEGIN -> writer < 0 && !stopped;
				case END -> writer >= 0;
				case REPEAT -> firstBudget != 0;
				case BUDGET_END -> firstBudget != 0;
				case RELEASE -> outcome == 0 && !captured && !sockets[0].lost;
				case CHECKPOINT -> released && outcome == 0 && !sockets[0].lost;
				case FACT -> captured && outcome == 0 && !sockets[0].lost;
				default -> !stopped;
			};
		}

		void apply(Event e)
		{
			switch (e)
			{
				case TRUE, FALSE, THROW ->
				{
					lateSuccess = e == Event.TRUE && now >= 10;
					notified = true;
					owner.saveNotification.set(List.of(BridgeFrames.bye(e == Event.TRUE
							? BridgeFrames.BYE_NORMAL : BridgeFrames.BYE_INTERNAL)));
					owner.serviceCloseResult(now);
					outcome = owner.closeOutcome.getFirst().field(1).value().u() == BridgeFrames.BYE_NORMAL ? 1 : 2;
					claim();
				}
				case DEADLINE ->
				{
					now = 10;
					if (outcome == 0)
					{
						owner.completeCloseResult(List.of(BridgeFrames.bye(BridgeFrames.BYE_INTERNAL)));
						outcome = 2; claim();
					}
				}
				case BUDGET_END -> now = firstBudget;
				case ADVANCE_SAVE_CLOCK -> now = Math.max(now, 10);
				case FRAME2 ->
				{
					if (!sockets[1].terminal)
					{
						sockets[1].terminal = true; sockets[1].closing = true; sockets[1].awaitingResult = false;
					}
				}
				case EOF1, EOF2 ->
				{
					int n = e == Event.EOF1 ? 0 : 1;
					sockets[n].lost = true;
					if (recipient == n && !sockets[n].saveReplyStarted) { recipient = -1; claim(); }
				}
				case CLOSE1 -> { preferred = 0; owner.closeReplyTransport = sockets[0]; }
				case CLOSE2 -> { preferred = 1; owner.closeReplyTransport = sockets[1]; }
				case SHUTDOWN, REPEAT ->
				{
					boolean claimed = budget.claim(now, 12);
					if (claimed) firstBudget = budget.deadline();
					assertEquals(firstBudget, budget.deadline());
				}
				case RELEASE ->
				{
					released = true;
					sockets[0].releaseAfterCheckpoint = true;
					sockets[0].afterCheckpoint.add(OLD); sockets[0].held++;
				}
				case CHECKPOINT ->
				{
					captured = true; snapshotPending = true;
					sockets[0].releaseAfterCheckpointCount = sockets[0].afterCheckpoint.size();
					sockets[0].checkpoint = PRODUCER;
				}
				case FACT -> { sockets[0].afterCheckpoint.add(FACT); sockets[0].held++; }
				case BEGIN ->
				{
					for (int n = 0; n != 2; n++)
					{
						var t = sockets[n];
						if (t.lost) continue;
						BridgeLoopback.applyOutputFlags(t);
						if (n == 0 && snapshotPending)
						{
							t.queued.add(SNAPSHOT); t.held++; snapshotPending = false;
							t.checkpoint = null;
							while (!t.afterCheckpoint.isEmpty()) t.queued.add(t.afterCheckpoint.remove());
						}
						if (!t.queued.isEmpty()) { t.writing = t.queued.remove(); writer = n; break; }
					}
				}
				case END ->
				{
					var t = sockets[writer];
					if (!t.lost)
					{
						if (t.writing == t.saveReplyFrame)
						{
							byes++; t.saveReplyStarted = true;
							try
							{
								var decoded = Codec.bridge().read(new ByteArrayInputStream(t.writing));
								assertEquals(1, decoded.size());
								assertEquals(owner.closeOutcome.getFirst().type(), decoded.getFirst().type());
								assertEquals(owner.closeOutcome.getFirst().field(1).value().u(), decoded.getFirst().field(1).value().u());
							}
							catch (Exception failure) { throw new AssertionError(failure); }
						}
						if (t.writing == OLD || t.writing == SNAPSHOT || t.writing == FACT)
						{
							int seq = t.writing[0]; assertTrue(seq > lastSeq, "Fact sequence regressed"); lastSeq = seq;
						}
					}
					t.held -= t.writing.length; t.writing = null; writer = -1;
					BridgeLoopback.applyOutputFlags(t);
				}
			}
			boolean drained = outcome != 0 && Arrays.stream(sockets).allMatch(t -> t.lost || BridgeLoopback.outputDrained(t));
			if (firstBudget != 0) stopped = BridgeShutdownBudget.canStop(now, firstBudget, drained);
		}

		void claim()
		{
			owner.serviceCloseResult(now);
			recipient = owner.outcomeRecipient == sockets[0] ? 0 : owner.outcomeRecipient == sockets[1] ? 1 : -1;
		}

		void check(List<Event> trace)
		{
			assertTrue(byes <= 1, trace.toString());
			if (lateSuccess) assertEquals(2, outcome, trace.toString());
			for (var t : sockets)
			{
				int bytes = t.writing == null ? 0 : t.writing.length;
				for (byte[] frame : t.queued) bytes += frame.length;
				for (byte[] frame : t.afterCheckpoint) bytes += frame.length;
				assertEquals(bytes, t.held, trace.toString());
				assertTrue(t.held >= 0 && t.held <= FULL.length, trace.toString());
			}
			if (recipient >= 0 && byes == 0 && !sockets[recipient].lost)
				assertFalse(BridgeLoopback.outputDrained(sockets[recipient]), "A claimed Bye disappeared: " + trace);
		}

		void drain(List<Event> trace)
		{
			// Fair writable continuation of every prefix. This detects lost pending Bye
			// and sequence regressions even when the bounded prefix ends inside write.
			for (int turn = 0; turn < 8; turn++)
			{
				if (writer >= 0) apply(Event.END);
				if (stopped) break;
				apply(Event.BEGIN);
				if (writer < 0) break;
			}
			check(trace);
			if (outcome != 0 && recipient >= 0 && !sockets[recipient].lost && (!stopped || now < firstBudget))
				assertEquals(1, byes, "Writable continuation lost the outcome: " + trace);
		}

		String key()
		{
			StringBuilder b = new StringBuilder(now + ":" + outcome + ":" + recipient + ":" + writer + ":"
					+ byes + ":" + lastSeq + ":" + firstBudget + ":" + snapshotPending + ":" + preferred
					+ ":" + notified + ":" + released + ":" + captured + ":" + stopped + ":" + lateSuccess);
			for (var t : sockets)
			{
				b.append(':').append(t.lost).append(':').append(t.held).append(':').append(t.terminalReply != null);
				b.append(':').append(t.terminal).append(':').append(t.releaseAfterCheckpoint).append(':').append(t.releaseAfterCheckpointCount);
				b.append(':').append(t.checkpoint != null).append(':').append(t.saveReplyStarted);
				b.append('w').append(t.writing == null ? -1 : t.writing == FULL ? 0 : t.writing == t.saveReplyFrame ? 127 : t.writing[0]);
				for (byte[] f : t.queued) b.append('q').append(f == FULL ? 0 : f == t.saveReplyFrame ? 127 : f[0]);
				for (byte[] f : t.afterCheckpoint) b.append('a').append(f[0]);
			}
			return b.toString();
		}
	}
}
