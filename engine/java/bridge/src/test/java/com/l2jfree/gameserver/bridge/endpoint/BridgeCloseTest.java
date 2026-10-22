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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * After its own bye the listener closes the TCP socket. The peer does not have to close first.
 */
class BridgeCloseTest
{
	@Test
	void theServerClosesAfterItsBye() throws Exception
	{
		byte[] secret = new byte[32];
		for (int i = 0; i < secret.length; i++)
			secret[i] = (byte) (i + 1);
		StringBuilder hex = new StringBuilder();
		for (byte b : secret)
			hex.append(String.format("%02x", b));
		
		BridgeLoopback loop = null;
		for (int port = 19016; port < 19040 && loop == null; port++)
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", hex.toString());
			try
			{
				loop = BridgeLoopback.open(BridgeSettings.parse(properties), null);
			}
			catch (IllegalStateException e)
			{
				loop = null;
			}
		}
		assertThat(loop).isNotNull();
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress("127.0.0.1", loop.port()), 1000);
			socket.setSoTimeout(2000);
			byte[] wrong = secret.clone();
			wrong[0] ^= 1;
			socket.getOutputStream().write(frame(open(wrong)));
			socket.getOutputStream().flush();
			
			InputStream in = socket.getInputStream();
			int read = 0;
			byte[] buf = new byte[64];
			int n;
			while ((n = in.read(buf)) >= 0)
				read += n;
			assertThat(read).isGreaterThan(0);
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
}
