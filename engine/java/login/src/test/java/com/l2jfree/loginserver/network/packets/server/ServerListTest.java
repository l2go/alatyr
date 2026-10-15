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
package com.l2jfree.loginserver.network.packets.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.loginserver.network.L2Client;

/** The server list shows the one world of the platform with the host that suits the client. */
class ServerListTest
{
	/** A world that reports the given status and answers the client address with the host for it. */
	private static WorldPort world(WorldStatus status)
	{
		return new WorldPort() {
			@Override
			public WorldStatus status()
			{
				return status;
			}
			
			@Override
			public String addressFor(String clientIp)
			{
				return clientIp.startsWith("10.") ? "10.0.0.1" : "203.0.113.5";
			}
			
			@Override
			public void kick(String account)
			{
			}
			
			@Override
			public void expect(String clientIp)
			{
			}
		};
	}
	
	private static WorldStatus status(ServerStatus state, int maxPlayers, int ageLimit)
	{
		return new WorldStatus(3, state, 7777, 500, maxPlayers, ageLimit, true, true, true, false, false, false);
	}
	
	@Test
	void withoutAWorldTheListIsEmpty() throws Exception
	{
		ByteBuffer out = write(new ServerList(null, client("192.0.2.7", 1)), 1);
		
		assertThat(out.get()).isEqualTo((byte)0x04);
		assertThat(out.get()).isZero(); // number of servers
		assertThat(out.get()).isZero(); // last server
		assertThat(out.hasRemaining()).isFalse();
	}
	
	@Test
	void showsTheWorldWithTheHostForTheClientAndTheCounts() throws Exception
	{
		ByteBuffer out = write(new ServerList(world(status(ServerStatus.STATUS_AUTO, 1000, 0)), client("192.0.2.7", 3)),
				3);
		
		assertThat(out.get()).isEqualTo((byte)0x04);
		assertThat(out.get()).isEqualTo((byte)1);
		assertThat(out.get()).isEqualTo((byte)3); // last server, online
		assertThat(out.get()).isEqualTo((byte)3); // id
		assertThat(new byte[] { out.get(), out.get(), out.get(), out.get() }).containsExactly(203, 0, 113, 5);
		assertThat(out.getInt()).isEqualTo(7777);
		assertThat(out.get()).isZero(); // age limit
		assertThat(out.get()).isEqualTo((byte)1); // pvp
		assertThat(out.getShort()).isEqualTo((short)500);
		assertThat(out.getShort()).isEqualTo((short)1000);
		assertThat(out.get()).isEqualTo((byte)1); // online
		assertThat(out.getInt()).isEqualTo(0x02); // clock
		assertThat(out.get()).isEqualTo((byte)1); // brackets
		assertThat(out.hasRemaining()).isFalse();
	}
	
	@Test
	void internalClientsGetTheInternalHost() throws Exception
	{
		ByteBuffer out = write(new ServerList(world(status(ServerStatus.STATUS_AUTO, 100, 0)), client("10.1.2.3", 1)), 1);
		
		out.position(4);
		assertThat(new byte[] { out.get(), out.get(), out.get(), out.get() }).containsExactly(10, 0, 0, 1);
	}
	
	@Test
	void aDownWorldIsListedButNotPreselected() throws Exception
	{
		ByteBuffer out = write(new ServerList(world(status(ServerStatus.STATUS_DOWN, 100, 18)), client("192.0.2.7", 3)),
				3);
		
		assertThat(out.get(1)).isEqualTo((byte)1); // number of servers
		assertThat(out.get(2)).isZero(); // last server not offered
		assertThat(out.get(12)).isEqualTo((byte)18); // age limit, as 2.x showed it for a down server
		assertThat(out.get(18)).isZero(); // online flag
	}
	
	private static L2Client client(String ip, int lastServerId)
	{
		L2Client client = mock(L2Client.class);
		when(client.getIp()).thenReturn(ip);
		when(client.getLastServerId()).thenReturn(lastServerId);
		return client;
	}
	
	/** Writes the packet the way the network core does: into the buffer that the writing thread attaches. */
	private static ByteBuffer write(ServerList packet, int lastServerId) throws Exception
	{
		ByteBuffer buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
		Class<?> sendable = Class.forName("com.l2jfree.network.SendablePacket");
		Method attach = sendable.getDeclaredMethod("attachBuffer", ByteBuffer.class);
		Method restore = sendable.getDeclaredMethod("restoreBuffer", ByteBuffer.class);
		attach.setAccessible(true);
		restore.setAccessible(true);
		Object previous = attach.invoke(null, buffer);
		try
		{
			L2Client client = mock(L2Client.class);
			when(client.getLastServerId()).thenReturn(lastServerId);
			packet.write(client);
		}
		finally
		{
			restore.invoke(null, previous);
		}
		buffer.flip();
		return buffer;
	}
}
