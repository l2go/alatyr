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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;

/**
 * A bye that cannot be written still closes on the 100 ms deadline. The selector does not wait out a full second.
 */
class BridgeCloseDeadlineTest
{
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "eof", "close", "malformed", "auth", "opening", "read", "write" })
	void unboundTerminalEventsNeverCreateASessionAndReleaseTheirOnlySlot(String event) throws Exception
	{
		BridgeLoopback loop = listener();
		Transport transport = new Transport(new byte[32]);
		SocketChannel socket = mock(SocketChannel.class);
		when(socket.write(any(ByteBuffer.class))).thenAnswer(call -> ((ByteBuffer)call.getArgument(0)).remaining());
		when(socket.read(any(ByteBuffer.class))).thenReturn(-1);
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.channel()).thenReturn(socket);
		when(key.attachment()).thenReturn(transport);
		when(key.isReadable()).thenReturn(event.equals("eof"));
		try
		{
			loop.account();
			switch (event)
			{
				case "opening" -> BridgeLoopback.expire(transport, transport.openingDeadline);
				case "read" -> { transport.readDeadline = 1; BridgeLoopback.expire(transport, 1); }
				case "write" -> { transport.writeDeadline = 1; BridgeLoopback.expire(transport, 1); }
				case "close" -> loop.acceptFrame(transport, java.util.List.of(com.l2jfree.gameserver.bridge.wire.Envelope.of(
						com.l2jfree.gameserver.bridge.wire.Schemas.CLOSE, java.util.List.of(
						com.l2jfree.gameserver.bridge.wire.Field.u(1, true, 1), com.l2jfree.gameserver.bridge.wire.Field.u(2, true, 1),
						com.l2jfree.gameserver.bridge.wire.Field.u(3, true, 0)))));
				case "malformed" -> loop.acceptFrame(transport, java.util.List.of(BridgeFrames.pong(1)));
				case "auth" -> loop.acceptFrame(transport, java.util.List.of(com.l2jfree.gameserver.bridge.wire.Envelope.of(
						com.l2jfree.gameserver.bridge.wire.Schemas.RESUME, java.util.List.of(
						com.l2jfree.gameserver.bridge.wire.Field.u(1, true, 1), com.l2jfree.gameserver.bridge.wire.Field.u(2, true, 2),
						com.l2jfree.gameserver.bridge.wire.Field.data(3, true, new byte[] { 1 }),
						com.l2jfree.gameserver.bridge.wire.Field.data(4, true, "bridge/1".getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
				default -> { }
			}
			loop.serve(key);
			assertThat(transport.client).isNull();
			assertThat(transport.session).isNull();
			assertThat(transport.physicallyClosed).isTrue();
			verify(socket).close();
			verify(key).cancel();
			assertThat(loop.transports()).isZero();
			loop.serve(key); // Even a stale key cannot release the slot or close the channel twice.
			verify(socket, org.mockito.Mockito.times(1)).close();
			assertThat(loop.transports()).isZero();
		}
		finally { loop.close(); }
	}

	@Test
	void shutdownClosesAnIdleAcceptedPhysicalSocketWithoutTheOpeningWait() throws Exception
	{
		BridgeLoopback loop = listener();
		try (var server = java.nio.channels.ServerSocketChannel.open())
		{
			server.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0));
			try (var peer = SocketChannel.open(server.getLocalAddress()))
			{
				SocketChannel accepted = server.accept();
				loop.receive(accepted);
				assertThat(loop.transports()).isEqualTo(1);
				loop.close(100);
				assertThat(accepted.isOpen()).isFalse();
				assertThat(accepted.keyFor(getSelector(loop))).isNull();
				assertThat(loop.transports()).isZero();
				peer.socket().setSoTimeout(1000);
				assertThat(peer.socket().getInputStream().read()).isEqualTo(-1);
			}
		}
		finally { if (loop.alive()) loop.close(0); }
	}

	private static java.nio.channels.Selector getSelector(BridgeLoopback loop) throws Exception
	{
		var field = BridgeLoopback.class.getDeclaredField("selector");
		field.setAccessible(true);
		return (java.nio.channels.Selector)field.get(loop);
	}

	@Test
	void aCompleteFrameResetCannotBeExpiredByConcurrentSinkPublication() throws Exception
	{
		BridgeLoopback loop = listener();
		Transport transport = new Transport(new byte[32]);
		transport.opened = true;
		var frame = new java.io.ByteArrayOutputStream();
		com.l2jfree.gameserver.bridge.wire.Codec.bridge().write(frame, java.util.List.of(BridgeFrames.pong(1)));
		transport.pending.write(frame.toByteArray());
		synchronized (transport) { transport.readDeadline = System.nanoTime() - 1; }
		var reset = new java.util.concurrent.CountDownLatch(1);
		var published = new java.util.concurrent.CountDownLatch(1);
		Thread sender = new Thread(() ->
		{
			try
			{
				if (!reset.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("reset gate");
				loop.fromSink(transport, java.util.List.of(BridgeFrames.pong(2)));
			}
			catch (InterruptedException failure) { throw new AssertionError(failure); }
			finally { published.countDown(); }
		});
		try
		{
			sender.start();
			assertThat(BridgeLoopback.takeFrame(transport)).isNotNull();
			reset.countDown();
			assertThat(published.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			loop.serviceControl(transport);
			assertThat(transport.readDeadline).isZero();
			assertThat(transport.terminal).isFalse();
			assertThat(transport.closing).isFalse();
		}
		finally { reset.countDown(); sender.join(5000); loop.close(); }
	}

	@Test
	void openingExpiresAtTenSecondsEvenWithoutAnInputByte()
	{
		Transport transport = new Transport(new byte[32]);
		assertThat(BridgeLoopback.OPENING_NS).isEqualTo(10_000_000_000L);
		BridgeLoopback.expire(transport, transport.openingDeadline - 1);
		assertThat(transport.closing).isFalse();
		BridgeLoopback.expire(transport, transport.openingDeadline);
		BridgeLoopback.applyOutputFlags(transport);
		assertThat(transport.closing).isTrue();
		assertThat(transport.queued).hasSize(1);
		BridgeLoopback.expire(transport, transport.openingDeadline + 1);
		assertThat(transport.queued).hasSize(1);
	}

	@Test
	void aPartialPrefixKeepsItsDeadlineUntilTheWholeFrameArrives() throws Exception
	{
		Transport transport = new Transport(new byte[32]);
		transport.opened = true;
		assertThat(BridgeLoopback.FRAME_READ_NS).isEqualTo(5_000_000_000L);
		long deadline = System.nanoTime() + BridgeLoopback.FRAME_READ_NS;
		transport.readDeadline = deadline;
		transport.pending.write(0x80);
		assertThat(BridgeLoopback.takeFrame(transport)).isNull();
		assertThat(transport.readDeadline).isEqualTo(deadline);
		BridgeLoopback.expire(transport, deadline - 1);
		assertThat(transport.closing).isFalse();
		BridgeLoopback.expire(transport, deadline);
		assertThat(transport.closing).isTrue();
	}

	@Test
	void anIdleOpenedTransportHasNoFrameReadDeadline()
	{
		Transport transport = new Transport(new byte[32]);
		transport.opened = true;
		BridgeLoopback.expire(transport);
		assertThat(transport.readDeadline).isZero();
		assertThat(transport.closing).isFalse();
	}

	@Test
	void aPartialPrefixExpiresWithoutWaitingForAnotherRead()
	{
		Transport transport = new Transport(new byte[32]);
		transport.opened = true;
		transport.pending.write(0x80);
		transport.readDeadline = System.nanoTime() - 1;
		BridgeLoopback.expire(transport);
		assertThat(transport.closing).isTrue();
		BridgeLoopback.applyOutputFlags(transport);
		assertThat(transport.queued).hasSize(1);
	}

	@Test
	void completedClientsReleaseBothRegistries()
	{
		com.l2jfree.gameserver.network.L2Client client = mock(com.l2jfree.gameserver.network.L2Client.class);
		BridgeLoopback loop = listener();
		int before = BridgeAdmission.sessionCount();
		try
		{
			loop.watch(client);
			BridgeAdmission.newSession(client);
			loop.reapClients();
			assertThat(loop.clientCount()).isEqualTo(1);
			assertThat(BridgeAdmission.sessionCount()).isEqualTo(before + 1);
			when(client.isBridgeDisconnectFinished()).thenReturn(true);
			loop.reapClients();
			assertThat(loop.clientCount()).isZero();
			assertThat(BridgeAdmission.sessionCount()).isEqualTo(before);
		}
		finally
		{
			loop.close();
		}
	}

	@Test
	void aSinkByeClosesTheSocketAfterItDrains() throws Exception
	{
		Transport transport = new Transport(new byte[32]);
		SocketChannel channel = mock(SocketChannel.class);
		when(channel.write(any(ByteBuffer.class))).thenAnswer(call -> ((ByteBuffer) call.getArgument(0)).remaining());
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.channel()).thenReturn(channel);
		when(key.attachment()).thenReturn(transport);
		BridgeLoopback loop = listener();
		try
		{
			loop.account();
			loop.fromSink(transport, java.util.List.of(BridgeFrames.bye(BridgeFrames.BYE_NORMAL)));
			loop.serve(key);
			verify(channel).close();
			assertThat(loop.transports()).isZero();
		}
		finally
		{
			loop.close();
		}
	}

	@Test
	void theSelectTimeoutIsTheByeDeadline()
	{
		long now = System.nanoTime();
		assertThat(BridgeLoopback.selectTimeout(now, now + 100_000_000L)).isEqualTo(100);
		assertThat(BridgeLoopback.selectTimeout(now, now + 5_000_000_000L)).isEqualTo(1000);
		assertThat(BridgeLoopback.selectTimeout(now, now - 1)).isEqualTo(1);
	}

	@Test
	void aBlockedWriteClosesWhenTheDeadlinePasses() throws Exception
	{
		Transport transport = new Transport(new byte[32]);
		BridgeLoopback.offer(transport, new byte[] { 1, 2, 3, 4 });
		BridgeLoopback.armClose(transport);
		synchronized (transport)
		{
			transport.closeDeadline = System.nanoTime() - 1;
		}
		SocketChannel channel = mock(SocketChannel.class);
		when(channel.write(any(ByteBuffer.class))).thenReturn(0);
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.isAcceptable()).thenReturn(false);
		when(key.isReadable()).thenReturn(false);
		when(key.channel()).thenReturn(channel);
		when(key.attachment()).thenReturn(transport);
		BridgeLoopback loop = listener();
		try
		{
			loop.account();
			loop.serve(key);
			verify(channel).close();
			assertThat(loop.transports()).isZero();
		}
		finally
		{
			loop.close();
		}
	}

	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("66");
		for (int port = 19340; port < 19360; port++)
		{
			java.util.Properties properties = new java.util.Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", hex.toString());
			try
			{
				return BridgeLoopback.open(BridgeSettings.parse(properties), null);
			}
			catch (IllegalStateException e)
			{
				// try the next port
			}
		}
		throw new IllegalStateException("no port");
	}
}
