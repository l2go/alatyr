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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.server.*;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.embedded.EmbeddedChannel;

/** Uses the real client FIFO and listener socket branches with isolated game state. */
class BridgeTransportRecoveryTest
{
	@Test
	void retiringAPendingCloseCannotResetTheSliceHeldByTheSelector() throws Exception
	{
		var saving = new CountDownLatch(1);
		var releaseSave = new CountDownLatch(1);
		var writing = new CountDownLatch(1);
		var releaseWrite = new CountDownLatch(1);
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		try (Fixture fixture = new Fixture(20028))
		{
			fixture.open();
			doAnswer(call ->
			{
				saving.countDown();
				assertThat(releaseSave.await(5, TimeUnit.SECONDS)).isTrue();
				return true;
			}).when(fixture.player).store(true, true);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			var active = new ByteArrayOutputStream();
			Codec.bridge().write(active, List.of(BridgeFrames.pong(1)));
			byte[] frame = active.toByteArray();
			BridgeLoopback.offer(fixture.first, frame);
			BridgeLoopback.offer(fixture.first, new byte[] { 99 }); // Never started: retirement must discard it.
			SocketChannel socket = mock(SocketChannel.class);
			when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
			{
				writing.countDown();
				assertThat(releaseWrite.await(5, TimeUnit.SECONDS)).isTrue();
				ByteBuffer slice = call.getArgument(0);
				int n = slice.remaining();
				slice.position(slice.limit());
				return n;
			});
			SelectionKey socketKey = key(socket, fixture.first, false);
			fixture.loop.account();
			fixture.loop.account();
			Thread selector = new Thread(() ->
			{
				try { fixture.loop.serve(socketKey); }
				catch (Throwable thrown) { failure.set(thrown); }
			}, "bridge-test-selector");
			try
			{
				try (var gate = new FifoGate(fixture.client))
				{
					fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
					fixture.loop.acceptFrame(replacement, List.of(Envelope.of(Schemas.CLOSE,
							List.of(Field.u(1, true, fixture.id), Field.u(2, true, 2), Field.u(3, true, 0)))));
					gate.release();
					assertThat(saving.await(5, TimeUnit.SECONDS)).isTrue();
					selector.start();
					assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
					releaseSave.countDown();
					barrier(fixture.client);
					barrier(fixture.client);
					synchronized (fixture.first)
					{
						assertThat(fixture.first.writing).isSameAs(frame);
						assertThat(fixture.first.writingAt).isZero();
						assertThat(fixture.first.held).isEqualTo(frame.length + 1);
						assertThat(fixture.first.queued).hasSize(1); // FIFO only flags retirement; selector owns cleanup.
						assertThat(fixture.first.retire).isTrue();
						assertThat(fixture.first.closing).isTrue();
					}
				}
			}
			finally
			{
				releaseSave.countDown();
				releaseWrite.countDown();
				selector.join(5000);
			}
			assertThat(selector.isAlive()).isFalse();
			assertThat(failure.get()).isNull();
			assertThat(fixture.first.held).isGreaterThanOrEqualTo(0);
			verify(socket, times(1)).write(any(ByteBuffer.class));
			verify(socket).close();
			verify(socketKey).cancel();
			assertThat(fixture.loop.transports()).isEqualTo(1);
			var bye = fixture.drain(replacement);
			assertThat(bye).hasSize(1);
			assertThat(bye.get(0).type()).isEqualTo(Schemas.BYE);
			assertThat(bye.get(0).field(1).value().u()).isEqualTo(BridgeFrames.BYE_NORMAL);
			assertThat(fixture.loop.transports()).isZero();
			verify(fixture.player).store(true, true);
			verify(fixture.player).deleteMe();
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "saved", "false", "exception", "deadline", "lost-saved", "lost-false", "lost-exception", "lost-deadline" })
	void aPendingCloseRetiresTheLiveFirstSocketAndFallsBackAfterReplyLoss(String outcome) throws Exception
	{
		var saving = new CountDownLatch(1);
		var releaseSave = new CountDownLatch(1);
		try (Fixture fixture = new Fixture(20026))
		{
			fixture.open(); // T1 stays live: there is deliberately no detach or grace.
			String save = outcome.replace("lost-", "");
			boolean lostReply = outcome.startsWith("lost-");
			doAnswer(call ->
			{
				saving.countDown();
				assertThat(releaseSave.await(5, TimeUnit.SECONDS)).isTrue();
				if (save.equals("exception")) throw new IllegalStateException("save fixture");
				return !save.equals("false");
			}).when(fixture.player).store(true, true);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			var firstBytes = new ByteArrayOutputStream();
			var replyBytes = new ByteArrayOutputStream();
			SocketChannel firstSocket = writingSocket(firstBytes);
			SocketChannel replySocket = writingSocket(replyBytes);
			when(replySocket.read(any(ByteBuffer.class))).thenReturn(-1);
			SelectionKey firstKey = key(firstSocket, fixture.first, false);
			SelectionKey replyKey = key(replySocket, replacement, lostReply);
			fixture.loop.account();
			fixture.loop.account();
			assertThat(fixture.loop.transports()).isEqualTo(2);
			try
			{
				try (var gate = new FifoGate(fixture.client))
				{
					fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
					fixture.loop.acceptFrame(replacement, List.of(Envelope.of(Schemas.CLOSE,
							List.of(Field.u(1, true, fixture.id), Field.u(2, true, 2), Field.u(3, true, 0)))));
					assertThat(replacement.queued).isEmpty();
					verify(fixture.player, never()).store(true, true);
					if (lostReply)
					{
						fixture.loop.serve(replyKey);
						verify(replySocket).close();
						verify(replyKey).cancel();
						assertThat(fixture.loop.transports()).isEqualTo(1);
					}
					gate.release();
					assertThat(saving.await(5, TimeUnit.SECONDS)).isTrue();
					assertThat(fixture.owner.transport).isSameAs(fixture.first);
					assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
					assertThat(replacement.client).isNull();
					assertThat(replacement.attempts).isNull();
					assertThat(replacement.checkpoint).isNull();
					assertThat(fixture.first.queued).isEmpty();
					assertThat(replacement.queued).isEmpty();
					if (save.equals("deadline"))
					{
						synchronized (replacement) { replacement.resultDeadline = System.nanoTime() - 1; }
						if (!lostReply) BridgeLoopback.expire(replacement, System.nanoTime());
					}
				}
			}
			finally { releaseSave.countDown(); }
			barrier(fixture.client);
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectFinished()).isTrue();
			assertThat(fixture.first.closing).isTrue();
			assertThat(fixture.first.closeDeadline).isNotZero();
			assertThat(fixture.first.attemptFence.valid).isFalse();
			if (!lostReply)
			{
				assertThat(fixture.first.lost).isTrue();
				assertThat(fixture.first.queued).isEmpty();
			}
			fixture.loop.serve(firstKey);
			verify(firstSocket).close();
			verify(firstKey).cancel();
			assertThat(fixture.owner.sessionTransports).doesNotContain(fixture.first);
			if (!lostReply)
			{
				assertThat(fixture.loop.transports()).isEqualTo(1);
				fixture.loop.serve(replyKey);
				verify(replySocket).close();
				verify(replyKey).cancel();
			}
			assertThat(fixture.loop.transports()).isZero();
			assertThat(lostReply ? replyBytes.size() : firstBytes.size()).isZero();
			byte[] answer = (lostReply ? firstBytes : replyBytes).toByteArray();
			var input = new ByteArrayInputStream(answer);
			var records = Codec.bridge().read(input);
			assertThat(input.available()).isZero();
			assertThat(records).hasSize(1);
			assertThat(records.get(0).type()).isEqualTo(Schemas.BYE);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(save.equals("saved")
					? BridgeFrames.BYE_NORMAL : BridgeFrames.BYE_INTERNAL);
			assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
			verify(fixture.pool.pool, never()).scheduleGeneral(any(Runnable.class), anyLong());
			verify(fixture.player).store(true, true);
			verify(fixture.player).deleteMe();
		}
	}

	private static SocketChannel writingSocket(ByteArrayOutputStream output) throws Exception
	{
		SocketChannel socket = mock(SocketChannel.class);
		when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
		{
			ByteBuffer bytes = call.getArgument(0);
			byte[] copy = new byte[bytes.remaining()];
			bytes.get(copy);
			output.write(copy);
			return copy.length;
		});
		return socket;
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "saved", "false", "exception", "deadline" })
	void aPendingCloseWaitsForItsSingleSaveOutcomeOnTheLiveSocket(String outcome) throws Exception
	{
		var saving = new CountDownLatch(1);
		var releaseSave = new CountDownLatch(1);
		try (Fixture fixture = new Fixture(20024))
		{
			fixture.open();
			doAnswer(call ->
			{
				saving.countDown();
				assertThat(releaseSave.await(5, TimeUnit.SECONDS)).isTrue();
				if (outcome.equals("exception")) throw new IllegalStateException("save fixture");
				return !outcome.equals("false");
			}).when(fixture.player).store(true, true);
			fixture.owner.detach(fixture.first);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			try
			{
				try (var gate = new FifoGate(fixture.client))
				{
					fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
					var close = Envelope.of(Schemas.CLOSE,
							List.of(Field.u(1, true, fixture.id), Field.u(2, true, 2), Field.u(3, true, 0)));
					fixture.loop.acceptFrame(replacement, List.of(close));
					fixture.loop.acceptFrame(replacement, List.of(close));
					assertThat(replacement.awaitingResult).isTrue();
					assertThat(replacement.terminal).isFalse();
					assertThat(replacement.queued).isEmpty();
					verify(fixture.player, never()).store(true, true);
					gate.release();
					assertThat(saving.await(5, TimeUnit.SECONDS)).isTrue();
					// Even after disconnect starts, no normal Bye can precede the store outcome.
					assertThat(replacement.queued).isEmpty();
					assertThat(replacement.client).isNull();
					assertThat(replacement.attempts).isNull();
					assertThat(replacement.checkpoint).isNull();
					assertThat(fixture.owner.transport).isNull();
					assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
					if (outcome.equals("deadline"))
					{
						synchronized (replacement) { replacement.resultDeadline = System.nanoTime() - 1; }
						BridgeLoopback.expire(replacement, System.nanoTime());
						assertThat(replacement.queued).hasSize(1);
					}
				}
			}
			finally { releaseSave.countDown(); }
			barrier(fixture.client);
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectFinished()).isTrue();
			BridgeLoopback.applyOutputFlags(replacement); // Explicit selector turn before inspecting its queue.
			assertThat(replacement.queued).hasSize(1);
			var records = fixture.drain(replacement);
			assertThat(records).hasSize(1);
			assertThat(records.get(0).type()).isEqualTo(Schemas.BYE);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(outcome.equals("saved")
					? BridgeFrames.BYE_NORMAL : BridgeFrames.BYE_INTERNAL);
			assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
			assertThat(replacement.client).isNull();
			assertThat(replacement.attempts).isNull();
			verify(fixture.player).store(true, true);
			verify(fixture.player).deleteMe();
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void closeAndResumeAdoptionHaveOneOrderAndSaveOnceEvenAfterEof(boolean closeFirst) throws Exception
	{
		try (Fixture fixture = new Fixture(closeFirst ? 20018 : 20020))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			Runnable originalGrace = timer.get();
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			try (var adoption = new FifoGate(fixture.client))
			{
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				if (!closeFirst)
				{
					adoption.release();
					barrier(fixture.client);
					assertThat(fixture.owner.transport).isSameAs(replacement);
				}
				try (var cleanup = closeFirst ? null : new FifoGate(fixture.client))
				{
					// Ignore an older epoch before the dialogue can latch closed, even before binding.
					var staleClose = Envelope.of(Schemas.CLOSE,
							List.of(Field.u(1, true, fixture.id), Field.u(2, true, 1), Field.u(3, true, 0)));
					fixture.loop.acceptFrame(replacement, List.of(staleClose));
					assertThat(replacement.explicitClose).isFalse();
					var close = Envelope.of(Schemas.CLOSE,
							List.of(Field.u(1, true, fixture.id), Field.u(2, true, 2), Field.u(3, true, 0)));
					fixture.loop.acceptFrame(replacement, List.of(close));
					fixture.loop.acceptFrame(replacement, List.of(close));
					assertThat(replacement.explicitClose).isTrue();
					assertThat(replacement.attemptFence.valid).isFalse();
					if (closeFirst)
					{
						assertThat(replacement.client).isNull();
						assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
					}
					else
						assertThat(fixture.client.isBridgeDisconnectRequested()).isTrue();
					var socket = mock(SocketChannel.class);
					when(socket.read(any(ByteBuffer.class))).thenReturn(-1);
					when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
					{
						ByteBuffer bytes = call.getArgument(0);
						int written = bytes.remaining();
						bytes.position(bytes.limit());
						return written;
					});
					fixture.loop.account();
					fixture.loop.serve(key(socket, replacement, true));
					assertThat(replacement.disconnectStarted).isTrue();
					verify(socket).close();
					assertThat(timer.get()).isSameAs(originalGrace);
					// Queue the original expiry too: it must not be invalidated by adopting this terminal socket,
					// and its cleanup request must never duplicate the explicit Close save.
					originalGrace.run();
					adoption.release();
					if (cleanup != null) cleanup.release();
					barrier(fixture.client);
					barrier(fixture.client);
					assertThat(fixture.client.isBridgeDisconnectFinished()).isTrue();
					assertThat(fixture.owner.recovery.epoch()).isEqualTo(closeFirst ? 1 : 2);
					if (closeFirst)
					{
						assertThat(replacement.client).isNull();
						assertThat(replacement.attempts).isNull();
						assertThat(replacement.checkpoint).isNull();
						assertThat(replacement.queued).isEmpty(); // A lost pending Close needs no undeliverable Bye.
					}
					verify(fixture.player).store(true, true);
					verify(fixture.player).deleteMe();
				}
			}
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "session", "epoch", "resumeEpoch" })
	void anInvalidPendingCloseCannotCloseTheAuthenticatedTarget(String mismatch) throws Exception
	{
		try (Fixture fixture = new Fixture(20022))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			try (var gate = new FifoGate(fixture.client))
			{
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				long epoch = mismatch.equals("resumeEpoch") ? 5 : 2;
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, epoch)));
				var close = Envelope.of(Schemas.CLOSE, List.of(
						Field.u(1, true, mismatch.equals("session") ? fixture.id + 1 : fixture.id),
						Field.u(2, true, mismatch.equals("epoch") ? 3 : epoch), Field.u(3, true, 0)));
				fixture.loop.acceptFrame(replacement, List.of(close));
				gate.release();
				barrier(fixture.client);
				assertThat(replacement.explicitClose).isFalse();
				assertThat(replacement.client).isNull();
				assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
				assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
				verify(fixture.player, never()).store(true, true);
				timer.get().run();
				barrier(fixture.client);
				barrier(fixture.client);
				verify(fixture.player).store(true, true);
			}
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void eofAndResumeAdoptionHaveOneOrderAndTheSurvivingGraceSaves(boolean eofFirst) throws Exception
	{
		try (Fixture fixture = new Fixture(eofFirst ? 20010 : 20012))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			Runnable originalGrace = timer.get();
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			try (var gate = new FifoGate(fixture.client))
			{
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				assertThat(replacement.client).isNull();
				if (!eofFirst)
				{
					gate.release();
					barrier(fixture.client);
					assertThat(fixture.owner.transport).isSameAs(replacement);
					originalGrace.run();
					barrier(fixture.client);
					assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
				}
				var socket = mock(SocketChannel.class);
				when(socket.read(any(ByteBuffer.class))).thenReturn(-1);
				fixture.loop.account();
				fixture.loop.serve(key(socket, replacement, true));
				verify(socket).close();
				assertThat(replacement.disconnectStarted).isTrue();
				assertThat(replacement.lost).isTrue();
				assertThat(replacement.attemptFence.valid).isFalse();
				gate.release();
				barrier(fixture.client);
				assertThat(fixture.owner.transport).isNull();
				assertThat(fixture.owner.recovery.epoch()).isEqualTo(eofFirst ? 1 : 2);
				if (eofFirst)
				{
					assertThat(replacement.client).isNull();
					assertThat(replacement.attempts).isNull();
					assertThat(timer.get()).isSameAs(originalGrace);
				}
				else
					assertThat(timer.get()).isNotSameAs(originalGrace);
				assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
				verify(fixture.player, never()).store(true, true);
				timer.get().run();
				barrier(fixture.client);
				barrier(fixture.client);
				assertThat(fixture.client.isBridgeDisconnectFinished()).isTrue();
				verify(fixture.player).store(true, true);
			}
		}
	}

	@Test
	void eofOnAPendingResumeCannotEvictTheStillLiveTransport() throws Exception
	{
		try (Fixture fixture = new Fixture(20014))
		{
			fixture.open();
			try (var pending = new FifoGate(fixture.client))
			{
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				var socket = mock(SocketChannel.class);
				when(socket.read(any(ByteBuffer.class))).thenReturn(-1);
				fixture.loop.account();
				fixture.loop.serve(key(socket, replacement, true));
				pending.release();
				barrier(fixture.client);
				assertThat(fixture.owner.transport).isSameAs(fixture.first);
				assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
				assertThat(fixture.first.lost).isFalse();
				assertThat(fixture.first.attemptFence.valid).isTrue();
				assertThat(replacement.client).isNull();
				verify(fixture.player, never()).store(true, true);
			}
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "lost", "closing", "terminal", "disconnectStarted" })
	void aCancelledReplacementCannotConsumeTheEpochOrCancelGrace(String flag) throws Exception
	{
		try (Fixture fixture = new Fixture(20016))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			try (var gate = new FifoGate(fixture.client))
			{
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				synchronized (replacement)
				{
					switch (flag)
					{
						case "lost" -> replacement.lost = true;
						case "closing" -> replacement.closing = true;
						case "terminal" -> replacement.terminal = true;
						case "disconnectStarted" -> replacement.disconnectStarted = true;
						default -> throw new AssertionError(flag);
					}
				}
				gate.release();
				barrier(fixture.client);
				assertThat(fixture.owner.transport).isNull();
				assertThat(replacement.client).isNull();
				assertThat(fixture.owner.recovery.epoch()).isEqualTo(1);
				timer.get().run();
				barrier(fixture.client);
				barrier(fixture.client);
				verify(fixture.player).store(true, true);
			}
		}
	}

	@Test
	void aDuplicateCaptureKeepsTheProducerAndTokenAfterBegin() throws Exception
	{
		try (Fixture fixture = new Fixture(20006))
		{
			fixture.seed();
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			var producer = fixture.first.checkpoint;
			var token = fixture.first.token;
			var socket = mock(SocketChannel.class);
			when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
			{
				ByteBuffer buffer = call.getArgument(0); int n = buffer.remaining(); buffer.position(buffer.limit()); return n;
			});
			fixture.loop.serve(key(socket, fixture.first, false));
			barrier(fixture.client);
			assertThat(producer.began()).isTrue();
			fixture.client.submit(fixture.owner::captureCheckpoint);
			barrier(fixture.client);
			assertThat(fixture.first.checkpoint).isSameAs(producer);
			assertThat(fixture.first.token).isEqualTo(token);
			assertThat(fixture.drain(fixture.first)).noneMatch(record -> record.type() == Schemas.CHECKPOINT
					&& record.field(1).value().u() == Schemas.BEGIN);
		}
	}

	@Test
	void aSocketWaitingForFifoResumeCannotCreateATemporaryAttemptTable() throws Exception
	{
		try (Fixture fixture = new Fixture(20004))
		{
			fixture.open();
			fixture.owner.detach(fixture.first);
			var started = new CountDownLatch(1);
			var release = new CountDownLatch(1);
			fixture.client.submit(() ->
			{
				started.countDown();
				try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("resume gate"); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			});
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
			try
			{
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				var intent = Codec.bridge().decode(java.util.HexFormat.of().parseHex("0a09180128093801b80c01")).getFirst();
				fixture.loop.acceptFrame(replacement, List.of(intent));
				assertThat(replacement.client).isNull();
				assertThat(replacement.attempts).isNull();
				assertThat(replacement.queued).isEmpty();
				assertThat(fixture.owner.attempts.retainedResults()).isEmpty();
				release.countDown();
				barrier(fixture.client);
				assertThat(fixture.drain(replacement)).noneMatch(record -> record.type() == Schemas.RESULT);
				assertThat(replacement.attempts).isSameAs(fixture.owner.attempts);
			}
			finally { release.countDown(); }
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void eofAfterSessionIdAssignmentButBeforeOpeningEndStillFullyDisconnects(boolean prefixWritten) throws Exception
	{
		try (Fixture fixture = new Fixture(prefixWritten ? 20002 : 20000))
		{
			fixture.seed();
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			assertThat(fixture.owner.id).isEqualTo(fixture.id);
			assertThat(fixture.first.opened).isFalse();
			var lost = mock(SocketChannel.class);
			if (prefixWritten)
			{
				when(lost.write(any(ByteBuffer.class))).thenAnswer(call ->
				{
					ByteBuffer buffer = call.getArgument(0); int n = buffer.remaining(); buffer.position(buffer.limit()); return n;
				});
				fixture.loop.serve(key(lost, fixture.first, false));
				barrier(fixture.client);
				assertThat(fixture.first.opened).isFalse();
			}
			when(lost.read(any(ByteBuffer.class))).thenReturn(-1);
			fixture.loop.account();
			fixture.loop.serve(key(lost, fixture.first, true));
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectRequested()).isTrue();
			verify(fixture.player).store(true, true);
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void relocationAfterBeginCancelsOldReadyWithoutNestingCheckpoints(boolean live) throws Exception
	{
		try (Fixture fixture = new Fixture(live ? 19998 : 19997))
		{
			if (live) fixture.open(); else fixture.seed();
			fixture.client.submit(() ->
			{
				if (live) fixture.owner.captureCheckpoint(); else fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1));
			});
			barrier(fixture.client);
			var oldToken = fixture.first.token;
			var bytes = new ByteArrayOutputStream();
			var socket = mock(SocketChannel.class);
			when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
			{
				ByteBuffer buffer = call.getArgument(0);
				byte[] copy = new byte[buffer.remaining()]; buffer.get(copy); bytes.write(copy); return copy.length;
			});
			// One selector turn writes Begin and the copied facts but cannot transfer End yet.
			fixture.loop.serve(key(socket, fixture.first, false));
			barrier(fixture.client);
			assertThat(fixture.first.checkpoint.began()).isTrue();
			doReturn(false).when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 8, 9, 10, 0, 1), fixture.player);
			fixture.client.submit(() -> fixture.owner.finishCheckpoint(fixture.first, oldToken));
			barrier(fixture.client);
			assertThat(fixture.first.attemptFence.ready).isFalse();
			doAnswer(call ->
			{
				doAnswer(capture -> { ((Runnable)capture.getArgument(0)).run(); return true; })
						.when(fixture.player).captureBridgeScene(any(Runnable.class));
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			fixture.owner.sink.ownTeleported(1);
			barrier(fixture.client);
			var records = new ArrayList<Envelope>();
			var input = new ByteArrayInputStream(bytes.toByteArray());
			while (input.available() != 0) records.addAll(Codec.bridge().read(input));
			records.addAll(fixture.drain(fixture.first));
			boolean inside = false;
			int ready = 0;
			for (Envelope record : records)
			{
				if (record.type() != Schemas.CHECKPOINT) continue;
				if (record.field(1).value().u() == Schemas.BEGIN)
				{
					assertThat(inside).isFalse(); inside = true;
				}
				else
				{
					assertThat(inside).isTrue(); inside = false;
					if (record.field(3).value().u() == 1) ready++;
				}
			}
			assertThat(inside).isFalse();
			assertThat(ready).isEqualTo(1);
			assertThat(records).filteredOn(record -> record.type() == Schemas.OPENED).hasSize(live ? 0 : 1);
		}
	}

	@Test
	void aFailedCaptureClosesInternallyWithoutPublishingReady() throws Exception
	{
		try (Fixture fixture = new Fixture(19996))
		{
			fixture.seed();
			doThrow(new IllegalArgumentException("capture capacity")).when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectRequested()).isTrue();
			assertThat(fixture.first.checkpoint).isNull();
			assertThat(fixture.first.attemptFence.ready).isFalse();
			verify(fixture.player).store(true, true);
		}
	}

	@Test
	void anExplicitCloseOfARecoverySessionSavesInsteadOfEnteringGrace() throws Exception
	{
		try (Fixture fixture = new Fixture(19994))
		{
			fixture.open();
			fixture.first.dialogue.accept(List.of(resume(fixture.id, 1)));
			var close = Envelope.of(Schemas.CLOSE, List.of(Field.u(1, true, fixture.id), Field.u(2, true, 1), Field.u(3, true, 0)));
			fixture.loop.acceptFrame(fixture.first, List.of(close));
			fixture.loop.acceptFrame(fixture.first, List.of(close));
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectRequested()).isTrue();
			assertThat(fixture.first.attemptFence.closing).isTrue();
			verify(fixture.player).store(true, true);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			assertThat(replacement.client).isNull();
			assertThat(fixture.drain(replacement)).noneMatch(record -> record.type() == Schemas.CHECKPOINT);
		}
	}

	@Test
	void aTeleportInsideTheActualEnterWorldDefersOpenedUntilTheQueuedCompletion() throws Exception
	{
		try (Fixture fixture = new Fixture(19992))
		{
			fixture.first.holdOpeningFacts = true;
			var stable = new java.util.concurrent.atomic.AtomicBoolean(true);
			doAnswer(call ->
			{
				if (!stable.get()) return false;
				((Runnable)call.getArgument(0)).run(); return true;
			}).when(fixture.player).captureBridgeScene(any(Runnable.class));
			when(fixture.player.getInventory()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.class));
			when(fixture.player.getInventory().getItems()).thenReturn(new com.l2jfree.gameserver.model.items.L2ItemInstance[0]);
			when(fixture.player.getKnownList()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.knownlist.PlayerKnownList.class));
			when(fixture.player.getMacroses()).thenReturn(mock(com.l2jfree.gameserver.model.MacroList.class));
			when(fixture.player.getFriendList()).thenReturn(mock(com.l2jfree.gameserver.model.L2FriendList.class));
			when(fixture.player.getAllShortCuts()).thenReturn(new com.l2jfree.gameserver.model.L2ShortCut[0]);
			when(fixture.player.getClassId()).thenReturn(com.l2jfree.gameserver.gameobjects.base.ClassId.HumanFighter);
			when(fixture.player.getStatus().getCurrentHp()).thenReturn(10.0);
			when(fixture.player.canReceiveAnnualPresent()).thenReturn(-1);
			doAnswer(call -> { fixture.owner.sink.deliver(new SkillList(fixture.player), fixture.player); return null; })
					.when(fixture.player).sendSkillList();
			doAnswer(call ->
			{
				stable.set(false);
				fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 90, 91, 92, 0, 1), fixture.player);
				fixture.owner.sink.ownTeleported(1);
				return null;
			}).when(fixture.player).teleToLocation(com.l2jfree.gameserver.model.mapregion.TeleportWhereType.Town);
			doAnswer(call ->
			{
				assertThat(fixture.first.checkpoint).isNull();
				assertThat(fixture.first.queued).isEmpty();
				stable.set(true);
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
			fixture.client.submit(() ->
			{
				try (var world = new OpeningWorld())
				{
					// The source handler itself chooses its town relocation while entering a stadium.
					when(world.olympiad.playerInStadia(fixture.player)).thenReturn(true);
					var packet = com.l2jfree.gameserver.network.packets.client.EnterWorld.forBridge();
					packet.bindDirect(fixture.client);
					packet.run();
					assertThat(packet.failed()).isFalse();
					assertThat(stable).isFalse();
					fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1));
					assertThat(fixture.first.checkpoint).isNull();
				}
				catch (Throwable problem) { failure.set(problem); }
			});
			barrier(fixture.client);
			barrier(fixture.client);
			assertThat(failure.get()).isNull();
			var records = fixture.drain(fixture.first);
			assertThat(records.getFirst().type()).isEqualTo(Schemas.CHECKPOINT);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.getLast().type()).isEqualTo(Schemas.OPENED);
			verify(fixture.player).completeBridgeTeleport(1);
		}
	}

	/** Isolates only the unrelated process-wide world services; EnterWorld and its packet delivery are real. */
	private static final class OpeningWorld implements AutoCloseable
	{
		private final List<org.mockito.MockedStatic<?>> mocks = new ArrayList<>();
		final com.l2jfree.gameserver.model.olympiad.Olympiad olympiad;

		OpeningWorld() throws java.sql.SQLException
		{
			var database = isolate(com.l2jfree.L2DatabaseFactory.class);
			var connection = mock(java.sql.Connection.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
			when(database.getConnection()).thenReturn(connection);
			isolate(com.l2jfree.gameserver.instancemanager.InstanceManager.class);
			isolate(com.l2jfree.gameserver.Announcements.class);
			isolate(com.l2jfree.gameserver.model.sevensigns.SevenSigns.class);
			isolate(com.l2jfree.gameserver.instancemanager.SiegeManager.class);
			var quests = isolate(com.l2jfree.gameserver.instancemanager.QuestManager.class);
			when(quests.getAllManagedScripts()).thenReturn(List.of());
			isolate(com.l2jfree.gameserver.model.quest.Quest.class);
			olympiad = isolate(com.l2jfree.gameserver.model.olympiad.Olympiad.class);
			isolate(com.l2jfree.gameserver.instancemanager.DimensionalRiftManager.class);
			isolate(com.l2jfree.gameserver.model.entity.Hero.class);
			isolate(com.l2jfree.gameserver.model.restriction.ObjectRestrictions.class);
			isolate(com.l2jfree.gameserver.instancemanager.CrownManager.class);
			isolate(com.l2jfree.gameserver.instancemanager.PetitionManager.class);
			isolate(com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager.class);
			isolate(com.l2jfree.gameserver.gameobjects.instance.L2ClassMasterInstance.class);
			isolate(com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions.class);
		}

		private <T> T isolate(Class<T> type)
		{
			T service = mock(type);
			mocks.add(mockStatic(type, call -> call.getMethod().getName().equals("getInstance")
					? service : org.mockito.Answers.RETURNS_DEFAULTS.answer(call)));
			return service;
		}

		@Override public void close()
		{
			for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
		}
	}

	@Test
	void villageRiseRunsTheSourceHandlerOnceAndRemainsSentUntilOwnCompletion() throws Exception
	{
		try (Fixture fixture = new Fixture(19990))
		{
			fixture.open();
			var stable = new java.util.concurrent.atomic.AtomicBoolean(true);
			doAnswer(call ->
			{
				if (!stable.get()) return false;
				((Runnable)call.getArgument(0)).run(); return true;
			}).when(fixture.player).captureBridgeScene(any(Runnable.class));
			when(fixture.player.isDead()).thenReturn(true);
			var result = new java.util.concurrent.atomic.AtomicReference<Envelope>();
			// The completion was queued from inside the handler and runs after its Attempt closes.
			doAnswer(call ->
			{
				assertThat(com.l2jfree.gameserver.network.bridge.EffectLedger.isActive()).isFalse();
				stable.set(true);
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			fixture.client.submit(() ->
			{
				try (var restrictions = mockStatic(com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions.class);
						var castles = mockStatic(com.l2jfree.gameserver.instancemanager.CastleManager.class);
						var regions = mockStatic(com.l2jfree.gameserver.instancemanager.MapRegionManager.class))
				{
					restrictions.when(() -> com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions.canRequestRevive(fixture.player)).thenReturn(true);
					var castle = mock(com.l2jfree.gameserver.instancemanager.CastleManager.class);
					castles.when(com.l2jfree.gameserver.instancemanager.CastleManager::getInstance).thenReturn(castle);
					var region = mock(com.l2jfree.gameserver.instancemanager.MapRegionManager.class);
					regions.when(com.l2jfree.gameserver.instancemanager.MapRegionManager::getInstance).thenReturn(region);
					var location = new com.l2jfree.gameserver.model.Location(90, 91, 92);
					when(region.getTeleToLocation(fixture.player, com.l2jfree.gameserver.model.mapregion.TeleportWhereType.Town)).thenReturn(location);
					doAnswer(call ->
					{
						stable.set(false);
						fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 90, 91, 92, 0, 1), fixture.player);
						fixture.owner.sink.ownTeleported(1);
						return null;
					}).when(fixture.player).teleToLocation(location, true);
					var attempt = BridgeExecution.run(fixture.client, com.l2jfree.gameserver.network.packets.client.RequestRestartPoint.forBridge());
					int stage = BridgeOutcome.match(attempt, 9, 0, 0, 0);
					result.set(Codec.bridge().decode(BridgeResults.encode(1, 9, stage, 0, attempt)).getFirst());
				}
			});
			barrier(fixture.client);
			barrier(fixture.client);
			assertThat(result.get().field(2).value().u()).isEqualTo(1);
			assertThat(result.get().field(7).value().u()).isEqualTo(2);
			assertThat(fixture.drain(fixture.first).getLast().field(3).value().u()).isEqualTo(1);
			verify(fixture.player).setIsPendingRevive(true);
			verify(fixture.player).completeBridgeTeleport(1);
		}
	}

	@Test
	void relocationBeforeOpeningBeginReplacesBufferedFactsWithOneFreshSnapshot() throws Exception
	{
		try (Fixture fixture = new Fixture(19988))
		{
			fixture.seed();
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			assertThat(fixture.first.checkpoint.began()).isFalse();
			fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
			assertThat(fixture.first.afterCheckpoint).isNotEmpty();
			fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 8, 9, 10, 0, 1), fixture.player);
			BridgeLoopback.applyOutputFlags(fixture.first); // Selector discards the cancelled generation's backlog.
			assertThat(fixture.first.queued).isEmpty();
			assertThat(fixture.first.afterCheckpoint).isEmpty();
			assertThat(fixture.first.held).isZero();
			fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
			assertThat(fixture.first.queued).isEmpty();
			fixture.client.submit(fixture.owner::captureCheckpoint);
			barrier(fixture.client);
			var records = fixture.drain(fixture.first);
			assertThat(records.getFirst().type()).isEqualTo(Schemas.CHECKPOINT);
			assertThat(records).filteredOn(record -> record.type() == Schemas.CHECKPOINT
					&& record.field(1).value().u() == Schemas.BEGIN).hasSize(1);
			assertThat(records.getLast().type()).isEqualTo(Schemas.OPENED);
		}
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	void resumeBeforeOrAfterOwnCompletionUsesTheNewSceneWithoutEarlyReady(boolean completeFirst) throws Exception
	{
		try (Fixture fixture = new Fixture(completeFirst ? 19986 : 19984))
		{
			fixture.open();
			var stable = new java.util.concurrent.atomic.AtomicBoolean(false);
			doAnswer(call ->
			{
				if (!stable.get()) return false;
				((Runnable)call.getArgument(0)).run(); return true;
			}).when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 90, 91, 92, 0, 1), fixture.player);
			fixture.owner.detach(fixture.first);
			doAnswer(call ->
			{
				stable.set(true);
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			if (completeFirst)
			{
				fixture.owner.sink.ownTeleported(1);
				barrier(fixture.client);
			}
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			if (!completeFirst)
			{
				assertThat(replacement.checkpoint).isNull();
				assertThat(replacement.queued).isEmpty();
				assertThat(replacement.attemptFence.ready).isFalse();
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				barrier(fixture.client);
				assertThat(replacement.checkpoint).isNull();
				assertThat(replacement.queued).isEmpty();
				fixture.owner.sink.ownTeleported(1);
				barrier(fixture.client);
			}
			var producer = replacement.checkpoint;
			var token = replacement.token;
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			assertThat(replacement.checkpoint).isSameAs(producer);
			assertThat(replacement.token).isEqualTo(token);
			assertThat(replacement.queued).isEmpty();
			var records = fixture.drain(replacement);
			assertThat(records).filteredOn(record -> record.type() == Schemas.FACT && record.field(67) != null).hasSize(1);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.getLast().type()).isEqualTo(Schemas.OPENED);
			verify(fixture.player).completeBridgeTeleport(1);
			verify(fixture.player, never()).store(true, true);
		}
	}

	@Test
	void resumeBeforeGraceExpiryKeepsTheClientEvenWhileRelocationDefersTheSnapshot() throws Exception
	{
		try (Fixture fixture = new Fixture(19980))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			doReturn(false).when(fixture.player).captureBridgeScene(any(Runnable.class));
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			timer.get().run();
			barrier(fixture.client);
			assertThat(fixture.owner.transport).isSameAs(replacement);
			assertThat(replacement.checkpoint).isNull();
			assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
			doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return true; })
					.when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.client.submit(fixture.owner::captureCheckpoint);
			barrier(fixture.client);
			assertThat(fixture.drain(replacement).getLast().type()).isEqualTo(Schemas.OPENED);
			verify(fixture.player, never()).store(true, true);
		}
	}

	@Test
	void graceExpiryBeforeResumeClosesTheSessionAndNeverAdoptsTheReplacement() throws Exception
	{
		try (Fixture fixture = new Fixture(19982))
		{
			fixture.open();
			var timer = new java.util.concurrent.atomic.AtomicReference<Runnable>();
			doAnswer(call -> { timer.set(call.getArgument(0)); return null; })
					.when(fixture.pool.pool).scheduleGeneral(any(Runnable.class), anyLong());
			fixture.owner.detach(fixture.first);
			timer.get().run();
			barrier(fixture.client);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			assertThat(fixture.client.isBridgeDisconnectRequested()).isTrue();
			assertThat(fixture.owner.transport).isNull();
			assertThat(replacement.client).isNull();
			assertThat(fixture.drain(replacement)).noneMatch(record -> record.type() == Schemas.CHECKPOINT);
			verify(fixture.player).store(true, true);
		}
	}

	@Test
	void aLateEofOnTheReplacedSocketCannotDetachTheAcceptedResume() throws Exception
	{
		try (Fixture fixture = new Fixture(19960))
		{
			fixture.open();
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			fixture.owner.detach(fixture.first);
			assertThat(fixture.owner.transport).isSameAs(replacement);
			assertThat(fixture.drain(replacement).getLast().type()).isEqualTo(Schemas.OPENED);
			assertThat(replacement.attemptFence.ready).isTrue();
			assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
		}
	}

	@Test
	void anInventoryObservationWhileDetachedIsPresentInTheRecoverySnapshot() throws Exception
	{
		try (Fixture fixture = new Fixture(19950))
		{
			fixture.open();
			var socket = mock(SocketChannel.class);
			when(socket.read(any(ByteBuffer.class))).thenReturn(-1);
			fixture.loop.account();
			fixture.loop.serve(key(socket, fixture.first, true));
			var item = mock(com.l2jfree.gameserver.model.items.L2ItemInstance.class);
			var template = mock(com.l2jfree.gameserver.model.items.templates.L2Item.class);
			when(item.getItem()).thenReturn(template);
			when(item.getItemId()).thenReturn(57);
			when(item.getItemDisplayId()).thenReturn(57);
			when(item.getObjectId()).thenReturn(80);
			when(item.getCount()).thenReturn(13L);
			// This full inventory packet cannot reach the lost socket, but must update retained current state.
			fixture.owner.sink.deliver(new ItemList(new com.l2jfree.gameserver.model.items.L2ItemInstance[] {item}, false), fixture.player);
			assertThat(fixture.first.queued).isEmpty();
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			var records = fixture.drain(replacement);
			var carried = records.stream().filter(record -> record.type() == Schemas.FACT && record.field(1).value().u() == 14)
					.findFirst().orElseThrow();
			assertThat(carried.field(23).value().s()).isEqualTo(13);
			assertThat(carried.field(24).value().items()).hasSize(1);
			assertThat(carried.field(31).value().u()).isEqualTo(1);
		}
	}

	@Test
	void relocationDuringOpeningKeepsTheQueueFreeAndEndsOnlyAfterCompletion() throws Exception
	{
		try (Fixture fixture = new Fixture(19930))
		{
			var stable = new java.util.concurrent.atomic.AtomicBoolean(false);
			doAnswer(call ->
			{
				if (!stable.get()) return false;
				((Runnable)call.getArgument(0)).run();
				return true;
			}).when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.seed();
			fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 1, 2, 3, 0, 1), fixture.player);
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			assertThat(fixture.first.checkpoint).isNull();
			assertThat(fixture.first.queued).isEmpty();
			doAnswer(call ->
			{
				stable.set(true);
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			fixture.owner.sink.ownTeleported(1);
			barrier(fixture.client);
			var records = fixture.drain(fixture.first);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(Schemas.BEGIN);
			int corrected = -1;
			int appeared = -1;
			for (int i = 0; i < records.size(); i++)
			{
				if (records.get(i).type() != Schemas.FACT) continue;
				if (records.get(i).field(67) != null) corrected = i;
				if (records.get(i).field(19) != null && records.get(i).field(19).value().u() == 1) appeared = i;
			}
			assertThat(corrected).isGreaterThan(appeared);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.get(records.size() - 1).type()).isEqualTo(Schemas.OPENED);
			verify(fixture.player).completeBridgeTeleport(1);
		}
	}

	@Test
	void eofRetainsTheCharacterAndResumeWritesACompleteCheckpointBeforeOpened() throws Exception
	{
		try (Fixture fixture = new Fixture(19890))
		{
			fixture.open();
			SocketChannel lost = mock(SocketChannel.class);
			when(lost.read(any(ByteBuffer.class))).thenReturn(-1);
			fixture.loop.account();
			fixture.loop.serve(key(lost, fixture.first, true));
			verify(lost).close();
			assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
			verify(fixture.player, never()).store(true, true);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			var records = fixture.drain(replacement);
			assertThat(records.get(0).type()).isEqualTo(Schemas.CHECKPOINT);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(Schemas.BEGIN);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.get(records.size() - 1).type()).isEqualTo(Schemas.OPENED);
			assertThat(records.get(records.size() - 1).field(2).value().u()).isEqualTo(fixture.id);
			assertThat(replacement.client).isSameAs(fixture.client);
			assertThat(fixture.client.getActiveChar()).isSameAs(fixture.player);
			verify(fixture.player, never()).store(true, true);
		}
	}

	@Test
	void oldQueuedWorkIsRefusedBeforeResumeAndReplayedOnlyAfterTheSnapshot() throws Exception
	{
		try (Fixture fixture = new Fixture(19910))
		{
			fixture.open();
			CountDownLatch running = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			fixture.client.submit(() ->
			{
				running.countDown();
				try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("queue gate"); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			});
			assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
			try
			{
				// Unknown essential rise fixture: admission is real, but stale execution must never reach its body.
				var intent = Codec.bridge().decode(java.util.HexFormat.of().parseHex("0a09180128093801b80c01")).get(0);
				fixture.first.attempts.accept(fixture.client, intent, fixture.first.attemptFence);
				SocketChannel lost = mock(SocketChannel.class);
				when(lost.read(any(ByteBuffer.class))).thenReturn(-1);
				fixture.loop.account();
				fixture.loop.serve(key(lost, fixture.first, true));
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				release.countDown();
				barrier(fixture.client);
				var records = fixture.drain(replacement);
				int result = -1;
				int end = -1;
				for (int i = 0; i < records.size(); i++)
				{
					if (records.get(i).type() == Schemas.RESULT) result = i;
					if (records.get(i).type() == Schemas.CHECKPOINT && records.get(i).field(1).value().u() == Schemas.END) end = i;
				}
				assertThat(result).isGreaterThan(end);
				assertThat(records.get(result).field(65).value().u()).isEqualTo(9);
				assertThat(records.get(result).field(2).value().u()).isEqualTo(3);
				assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
			}
			finally { release.countDown(); }
		}
	}

	private static Envelope resume(long id, long epoch)
	{
		return Envelope.of(Schemas.RESUME, List.of(Field.u(1, true, id), Field.u(2, true, epoch),
				Field.data(3, true, new byte[32]), Field.data(4, true, Codec.profileName())));
	}

	private static final class FifoGate implements AutoCloseable
	{
		private final CountDownLatch released = new CountDownLatch(1);

		FifoGate(L2Client client) throws Exception
		{
			var started = new CountDownLatch(1);
			client.submit(() ->
			{
				started.countDown();
				try { if (!released.await(5, TimeUnit.SECONDS)) throw new AssertionError("FIFO gate"); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			});
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		}

		void release() { released.countDown(); }
		@Override public void close() { release(); }
	}

	private static void barrier(L2Client client) throws Exception
	{
		CountDownLatch completed = new CountDownLatch(1);
		client.submit(completed::countDown);
		assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
	}

	private static SelectionKey key(SocketChannel channel, BridgeLoopback.Transport transport, boolean read)
	{
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.isReadable()).thenReturn(read);
		when(key.channel()).thenReturn(channel);
		when(key.attachment()).thenReturn(transport);
		return key;
	}

	private static final class Fixture implements AutoCloseable
	{
		final InlinePool pool = InlinePool.open();
		final EmbeddedChannel channel = new EmbeddedChannel();
		final BridgeLoopback loop;
		final BridgeLoopback.Transport first = new BridgeLoopback.Transport(new byte[32]);
		final BridgeLoopback.Session owner;
		final L2Client client;
		final L2Player player = BridgeTestPlayers.player();
		final long id;

		@SuppressWarnings("unchecked")
		Fixture(int port) throws Exception
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", "44".repeat(32));
			loop = BridgeLoopback.open(BridgeSettings.parse(properties), null);
			owner = loop.new Session(first);
			client = L2Client.forBridge(mock(NetworkServer.class), channel, InetAddress.getLoopbackAddress(), owner.sink);
			owner.sink.attach(client);
			owner.attach(client);
			id = BridgeAdmission.newSession(client);
			client.setActiveChar(player);
			client.setState(L2Client.GameClientState.IN_GAME);
			when(player.getObjectId()).thenReturn(70);
			when(player.getView()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.view.PlayerView.class));
			when(player.getView().getObjectId()).thenReturn(70);
			when(player.getView().getMovementSpeedMultiplier()).thenReturn(1.0f);
			when(player.getAppearance()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance.class));
			when(player.getAppearance().getVisibleName()).thenReturn("Visible player");
			when(player.getStat()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.stat.PlayerStat.class));
			when(player.getStatus()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.status.PlayerStatus.class));
			when(player.getSortedAllSkills(anyBoolean())).thenReturn(new com.l2jfree.gameserver.model.skills.L2Skill[0]);
			when(player.store(true, true)).thenReturn(true);
			doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return true; }).when(player).captureBridgeScene(any(Runnable.class));
		}

		void open() throws Exception
		{
			seed();
			client.submit(() -> owner.opened(BridgeFrames.accepted(id, 1)));
			barrier(client);
			assertThat(drain(first)).isNotEmpty();
		}

		void seed()
		{
			first.holdOpeningFacts = true;
			owner.sink.deliver(new UserInfo(player), player);
			owner.sink.deliver(new ItemList(new com.l2jfree.gameserver.model.items.L2ItemInstance[0], false), player);
			owner.sink.deliver(new SkillList(player), player);
			owner.sink.deliver(new QuestList(player), player);
		}

		List<Envelope> drain(BridgeLoopback.Transport transport) throws Exception
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			SocketChannel socket = mock(SocketChannel.class);
			when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
			{
				ByteBuffer value = call.getArgument(0);
				byte[] copy = new byte[value.remaining()];
				value.get(copy);
				bytes.write(copy);
				return copy.length;
			});
			loop.serve(key(socket, transport, false));
			barrier(client);
			loop.serve(key(socket, transport, false));
			var result = new ArrayList<Envelope>();
			var input = new ByteArrayInputStream(bytes.toByteArray());
			while (input.available() != 0)
				result.addAll(Codec.bridge().read(input));
			return result;
		}

		@Override
		public void close() throws Exception
		{
			client.requestBridgeDisconnect();
			barrier(client);
			loop.close();
			channel.finishAndReleaseAll();
			pool.close();
			BridgeAdmission.reapSessions();
		}
	}
}
