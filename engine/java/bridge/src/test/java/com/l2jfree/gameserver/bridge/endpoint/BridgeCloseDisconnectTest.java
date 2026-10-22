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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.network.L2Client;

/**
 * An explicit close of an admitted client starts one full disconnect. A repeat does not start another.
 */
class BridgeCloseDisconnectTest
{
	@Test
	void anAdmittedCloseDisconnectsOnce() throws Exception
	{
		byte[] secret = new byte[32];
		for (int i = 0; i < secret.length; i++)
			secret[i] = (byte) (i + 1);
		Transport transport = new Transport(secret);
		L2Client client = mock(L2Client.class);
		transport.client = client;
		transport.dialogue.accept(List.of(open(secret)));
		BridgeLoopback loop = listener();
		try
		{
			Envelope close = Envelope.of(Schemas.CLOSE, List.of(Field.u(1, true, 1), Field.u(2, true, 1), Field.u(3, true, 0)));
			loop.acceptFrame(transport, List.of(close));
			loop.acceptFrame(transport, List.of(close));
			verify(client, times(1)).requestBridgeDisconnect();
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
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("44");
		for (int port = 19270; port < 19290; port++)
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
