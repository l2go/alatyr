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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.bridge.wire.WireException;

/**
 * Illegal length prefixes are frame errors for that transport. The listener keeps serving the next client.
 */
class BridgePrefixTest
{
	@Test
	void anOverflowingPrefixIsAFrameErrorBeforeAllocation()
	{
		assertThatThrownBy(() -> take(bytes(9, (byte) 0xff, (byte) 0x01))).isInstanceOf(WireException.class);
		assertThatThrownBy(() -> take(bytes(9, (byte) 0xff, (byte) 0x02))).isInstanceOf(WireException.class);
		assertThatThrownBy(() -> take(new byte[] { (byte) 0x80, 0x00 })).isInstanceOf(WireException.class);
	}
	
	@Test
	void aSplitIllegalPrefixStillFailsAsAFrame() throws Exception
	{
		byte[] prefix = bytes(9, (byte) 0xff, (byte) 0x01);
		Transport transport = new Transport(new byte[32]);
		transport.pending.write(prefix, 0, 5);
		assertThat(BridgeLoopback.takeFrame(transport)).isNull();
		transport.pending.write(prefix, 5, 5);
		assertThatThrownBy(() -> BridgeLoopback.takeFrame(transport)).isInstanceOf(WireException.class);
	}
	
	@Test
	void aBadPrefixClosesOnlyThatTransport() throws Exception
	{
		BridgeLoopback loop = listener();
		try (Socket first = connect(loop); Socket second = connect(loop))
		{
			first.getOutputStream().write(bytes(9, (byte) 0xff, (byte) 0x01));
			first.getOutputStream().flush();
			first.setSoTimeout(2000);
			assertThat(drain(first)).isGreaterThan(0);
			
			byte[] secret = new byte[32];
			secret[0] = 1;
			second.getOutputStream().write(frame(Envelope.of(Schemas.OPEN, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
					Field.data(3, true, Codec.profileName()), Field.data(4, true, secret),
					Field.data(5, true, "guest".getBytes(StandardCharsets.UTF_8)),
					Field.data(6, true, "Guest".getBytes(StandardCharsets.UTF_8)), Field.u(7, true, 1)))));
			second.getOutputStream().flush();
			second.setSoTimeout(2000);
			assertThat(drain(second)).isGreaterThan(0);
		}
		finally
		{
			loop.close();
		}
	}
	
	private static List<Envelope> take(byte[] prefix) throws Exception
	{
		Transport transport = new Transport(new byte[32]);
		transport.pending.write(prefix);
		return BridgeLoopback.takeFrame(transport);
	}
	
	private static byte[] bytes(int nines, byte nine, byte last)
	{
		byte[] out = new byte[nines + 1];
		for (int i = 0; i < nines; i++)
			out[i] = nine;
		out[nines] = last;
		return out;
	}
	
	private static int drain(Socket socket) throws Exception
	{
		InputStream in = socket.getInputStream();
		int read = 0;
		byte[] buf = new byte[64];
		int n;
		while ((n = in.read(buf)) >= 0)
			read += n;
		return read;
	}
	
	private static Socket connect(BridgeLoopback loop) throws Exception
	{
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress("127.0.0.1", loop.port()), 1000);
		return socket;
	}
	
	private static byte[] frame(Envelope record) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Codec.bridge().write(out, List.of(record));
		return out.toByteArray();
	}
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("22");
		for (int port = 19210; port < 19230; port++)
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
