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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.L2Client;

/**
 * Shutdown stops admission before it waits. A socket opened during that wait is not kept.
 */
class BridgeShutdownAdmissionTest
{
	@Test
	void aSocketOpenedDuringShutdownIsNotAdmitted() throws Exception
	{
		BridgeLoopback loop = listener();
		AtomicBoolean finished = new AtomicBoolean();
		L2Client slow = mock(L2Client.class);
		doAnswer(invocation ->
		{
			new Thread(() ->
			{
				try
				{
					Thread.sleep(400);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				finished.set(true);
			}).start();
			return null;
		}).when(slow).requestBridgeDisconnect();
		when(slow.isBridgeDisconnectFinished()).thenAnswer(invocation -> finished.get());
		loop.watch(slow);
		Thread closer = new Thread(() -> loop.close(2_000));
		closer.start();
		Thread.sleep(30);
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress("127.0.0.1", loop.port()), 1000);
			socket.setSoTimeout(1000);
			assertThat(socket.getInputStream().read()).isEqualTo(-1);
		}
		closer.join(3000);
		assertThat(loop.clientCount()).isEqualTo(1);
	}
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("55");
		for (int port = 19300; port < 19320; port++)
		{
			Properties properties = new Properties();
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
