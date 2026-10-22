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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * A refusal that fails while it is being written closes that socket only. The listener keeps its slot count and still
 * serves the next client after one slot is released.
 */
class BridgeAcceptFailureTest
{
	@Test
	void aFailedRefusalDoesNotCloseTheListener() throws Exception
	{
		byte[] secret = new byte[32];
		for (int i = 0; i < secret.length; i++)
			secret[i] = 0x21;
		BridgeLoopback loop = listener(secret);
		try
		{
			SocketChannel held = mock(SocketChannel.class);
			SelectionKey heldKey = mock(SelectionKey.class);
			when(heldKey.isValid()).thenReturn(true);
			when(heldKey.isAcceptable()).thenReturn(false);
			when(heldKey.isReadable()).thenReturn(true);
			when(heldKey.channel()).thenReturn(held);
			when(heldKey.attachment()).thenReturn(new Transport(secret));
			for (int i = 0; i < BridgeLoopback.TRANSPORT_CAP - 1; i++)
				loop.account();
			loop.account();
			assertThat(loop.transports()).isEqualTo(BridgeLoopback.TRANSPORT_CAP);
			
			SocketChannel refused = mock(SocketChannel.class);
			when(refused.configureBlocking(anyBoolean())).thenReturn(refused);
			when(refused.write(any(ByteBuffer.class))).thenThrow(new java.io.IOException("reset"));
			loop.receive(refused);
			
			verify(refused).close();
			assertThat(loop.transports()).isEqualTo(BridgeLoopback.TRANSPORT_CAP);
			assertThat(loop.alive()).isTrue();
			
			when(held.read(any(ByteBuffer.class))).thenThrow(new java.io.IOException("reset"));
			loop.serve(heldKey);
			assertThat(loop.transports()).isEqualTo(BridgeLoopback.TRANSPORT_CAP - 1);
			
			try (Socket socket = new Socket())
			{
				socket.connect(new InetSocketAddress("127.0.0.1", loop.port()), 1000);
				socket.setSoTimeout(2000);
				byte[] wrong = secret.clone();
				wrong[0] ^= 0x01;
				socket.getOutputStream().write(frame(open(wrong)));
				socket.getOutputStream().flush();
				byte[] buf = new byte[256];
				int read = 0;
				int n;
				while ((n = socket.getInputStream().read(buf, read, buf.length - read)) >= 0)
				{
					read += n;
					if (read == buf.length)
						break;
				}
				assertThat(read).isGreaterThan(0);
				List<Envelope> records = Codec.bridge().read(new ByteArrayInputStream(buf, 0, read));
				assertThat(records.get(0).field(1).value().u()).isEqualTo(BridgeFrames.BYE_AUTH);
			}
			assertThat(loop.alive()).isTrue();
		}
		finally
		{
			loop.close();
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
	
	private static BridgeLoopback listener(byte[] secret)
	{
		StringBuilder hex = new StringBuilder();
		for (byte value : secret)
			hex.append(String.format("%02x", value));
		for (int port = 19510; port < 19530; port++)
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
