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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.network.L2Client;

/**
 * A TCP end on a bound session starts the one full disconnect and closes that socket.
 */
class BridgeEofDisconnectTest
{
	@Test
	void endOfStreamDisconnectsTheBoundClient() throws Exception
	{
		BridgeLoopback loop = listener();
		try
		{
			L2Client client = mock(L2Client.class);
			Transport transport = new Transport(new byte[32]);
			transport.client = client;
			SocketChannel channel = mock(SocketChannel.class);
			when(channel.read(any(ByteBuffer.class))).thenReturn(-1);
			SelectionKey key = mock(SelectionKey.class);
			when(key.isValid()).thenReturn(true);
			when(key.isAcceptable()).thenReturn(false);
			when(key.isReadable()).thenReturn(true);
			when(key.channel()).thenReturn(channel);
			when(key.attachment()).thenReturn(transport);
			loop.account();
			loop.serve(key);
			verify(client, times(1)).requestBridgeDisconnect();
			verify(channel).close();
			assertThat(loop.transports()).isZero();
			assertThat(loop.alive()).isTrue();
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
			hex.append("77");
		for (int port = 19370; port < 19390; port++)
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
