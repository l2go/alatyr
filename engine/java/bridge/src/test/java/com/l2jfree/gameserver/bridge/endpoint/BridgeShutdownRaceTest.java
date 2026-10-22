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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.extension.GameServerContext;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

/**
 * A client built after shutdown has taken its list still receives one disconnect and is not admitted.
 */
class BridgeShutdownRaceTest
{
	@Test
	void aClientBuiltDuringShutdownIsDisconnectedWithoutAdmission() throws Exception
	{
		byte[] secret = new byte[32];
		for (int i = 0; i < secret.length; i++)
			secret[i] = 0x3c;
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> network = mock(NetworkServer.class);
		GameServerContext context = mock(GameServerContext.class);
		when(context.getNetworkServer()).thenReturn(network);
		BridgeLoopback loop = listener(secret, context);
		AtomicBoolean finished = new AtomicBoolean();
		L2Client slow = mock(L2Client.class);
		doAnswer(invocation -> null).when(slow).requestBridgeDisconnect();
		when(slow.isBridgeDisconnectFinished()).thenAnswer(invocation -> finished.get());
		loop.watch(slow);
		CountDownLatch atGate = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<L2Client> admitted = new AtomicReference<>();
		loop.holdBeforeWatch(client ->
		{
			admitted.set(client);
			atGate.countDown();
			try
			{
				assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		});
		try (InlinePool pool = InlinePool.open(); Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress("127.0.0.1", loop.port()), 1000);
			socket.getOutputStream().write(frame(open(secret)));
			socket.getOutputStream().flush();
			assertThat(atGate.await(5, TimeUnit.SECONDS)).isTrue();
			Thread closer = new Thread(() -> loop.close(4_000));
			closer.start();
			long end = System.nanoTime() + 2_000_000_000L;
			while (loop.admitting() && System.nanoTime() < end)
				Thread.sleep(5);
			assertThat(loop.admitting()).isFalse();
			release.countDown();
			long seen = System.nanoTime() + 3_000_000_000L;
			while (loop.clientCount() < 2 && System.nanoTime() < seen)
				Thread.sleep(5);
			assertThat(admitted.get()).isNotNull();
			assertThat(admitted.get().isBridgeDisconnectRequested()).isTrue();
			assertThat(loop.clientCount()).isEqualTo(2);
			finished.set(true);
			closer.join(5_000);
			assertThat(admitted.get().getState()).isEqualTo(L2Client.GameClientState.CONNECTED);
		}
		finally
		{
			loop.holdBeforeWatch(null);
			release.countDown();
			finished.set(true);
		}
	}
	
	private static Envelope open(byte[] secret)
	{
		return Envelope.of(Schemas.OPEN, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
				Field.data(3, true, Codec.profileName()), Field.data(4, true, secret),
				Field.data(5, true, "guest".getBytes(StandardCharsets.UTF_8)),
				Field.data(6, true, "Guest".getBytes(StandardCharsets.UTF_8)), Field.u(7, true, 1)));
	}
	
	private static byte[] frame(Envelope record) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Codec.bridge().write(out, List.of(record));
		return out.toByteArray();
	}
	
	private static BridgeLoopback listener(byte[] secret, GameServerContext context)
	{
		StringBuilder hex = new StringBuilder();
		for (byte value : secret)
			hex.append(String.format("%02x", value));
		for (int port = 19570; port < 19590; port++)
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", hex.toString());
			try
			{
				return BridgeLoopback.open(BridgeSettings.parse(properties), context);
			}
			catch (IllegalStateException e)
			{
				// try the next port
			}
		}
		throw new IllegalStateException("no port");
	}
}
