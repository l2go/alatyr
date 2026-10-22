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
