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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;

/**
 * A reset on one socket closes that transport and leaves the listener up for the next one.
 */
class BridgeIsolationTest
{
	@Test
	void oneBrokenSocketDoesNotStopTheListener() throws Exception
	{
		BridgeLoopback loop = listener();
		try
		{
			assertThat(loop.alive()).isTrue();
			int resting = loop.transports();
			serveReset(loop, true);
			assertThat(loop.transports()).isEqualTo(resting);
			assertThat(loop.alive()).isTrue();
			
			serveReset(loop, false);
			assertThat(loop.transports()).isEqualTo(resting);
			assertThat(loop.alive()).isTrue();
			
			SocketChannel next = mock(SocketChannel.class);
			when(next.read(any(ByteBuffer.class))).thenReturn(-1);
			SelectionKey key = key(next, new Transport(new byte[32]), true);
			loop.account();
			loop.serve(key);
			verify(next).close();
			assertThat(loop.transports()).isEqualTo(resting);
			assertThat(loop.alive()).isTrue();
		}
		finally
		{
			loop.close();
		}
	}
	
	private static void serveReset(BridgeLoopback loop, boolean read) throws Exception
	{
		SocketChannel channel = mock(SocketChannel.class);
		Transport transport = new Transport(new byte[32]);
		if (read)
			when(channel.read(any(ByteBuffer.class))).thenThrow(new IOException("reset"));
		else
		{
			BridgeLoopback.offer(transport, new byte[] { 1, 2, 3 });
			when(channel.write(any(ByteBuffer.class))).thenThrow(new IOException("reset"));
		}
		loop.account();
		loop.serve(key(channel, transport, read));
		verify(channel).close();
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
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("33");
		for (int port = 19240; port < 19260; port++)
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
