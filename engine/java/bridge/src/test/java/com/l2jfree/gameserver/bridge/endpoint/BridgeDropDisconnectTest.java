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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.network.L2Client;

/**
 * Closing the transport disconnects a bound character, including a bad frame and a full outbound queue.
 */
class BridgeDropDisconnectTest
{
	@Test
	void aBadFrameDisconnectsTheBoundClient() throws Exception
	{
		BridgeLoopback loop = listener(19400);
		try
		{
			L2Client client = mock(L2Client.class);
			Transport transport = new Transport(new byte[32]);
			transport.client = client;
			byte[] prefix = new byte[10];
			for (int i = 0; i < 9; i++)
				prefix[i] = (byte) 0xff;
			prefix[9] = 0x01;
			SocketChannel channel = mock(SocketChannel.class);
			when(channel.read(any(ByteBuffer.class))).thenAnswer(invocation ->
			{
				ByteBuffer buffer = invocation.getArgument(0);
				buffer.put(prefix);
				return prefix.length;
			});
			when(channel.write(any(ByteBuffer.class))).thenAnswer(invocation ->
			{
				ByteBuffer buffer = invocation.getArgument(0);
				int n = buffer.remaining();
				buffer.position(buffer.limit());
				return n;
			});
			loop.account();
			loop.serve(key(channel, transport, true));
			verify(client, times(1)).requestBridgeDisconnect();
			verify(channel).close();
		}
		finally
		{
			loop.close();
		}
	}
	
	@Test
	void aFullOutboundQueueDisconnectsTheBoundClient() throws Exception
	{
		BridgeLoopback loop = listener(19420);
		try
		{
			L2Client client = mock(L2Client.class);
			Transport transport = new Transport(new byte[32]);
			transport.client = client;
			ByteArrayOutputStream bye = new ByteArrayOutputStream();
			Codec.bridge().write(bye, List.of(BridgeFrames.bye(BridgeFrames.BYE_SLOW)));
			BridgeLoopback.offer(transport, new byte[BridgeLoopback.OUT_QUEUE_BYTES - bye.size() - 1]);
			BridgeLoopback.offer(transport, new byte[8]);
			SocketChannel channel = mock(SocketChannel.class);
			when(channel.write(any(ByteBuffer.class))).thenAnswer(invocation ->
			{
				ByteBuffer buffer = invocation.getArgument(0);
				int n = buffer.remaining();
				buffer.position(buffer.limit());
				return n;
			});
			loop.account();
			loop.serve(key(channel, transport, false));
			verify(client, times(1)).requestBridgeDisconnect();
			verify(channel).close();
		}
		finally
		{
			loop.close();
		}
	}
	
	private static SelectionKey key(SocketChannel channel, Transport transport, boolean read)
	{
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.isAcceptable()).thenReturn(false);
		when(key.isReadable()).thenReturn(read);
		when(key.channel()).thenReturn(channel);
		when(key.attachment()).thenReturn(transport);
		return key;
	}
	
	private static BridgeLoopback listener(int firstPort)
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("88");
		for (int port = firstPort; port < firstPort + 15; port++)
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
