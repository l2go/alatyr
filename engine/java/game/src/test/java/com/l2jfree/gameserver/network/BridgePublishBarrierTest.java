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
package com.l2jfree.gameserver.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.HuntTestPlayers;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.ClientSink;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.Channel;

/**
 * Shutdown and publication share one lock. A capture does not stay null across a later publication, and a publication
 * after the disconnect has started does not run.
 */
class BridgePublishBarrierTest
{
	@Test
	void publicationAfterDisconnectDoesNotRun() throws Exception
	{
		L2Client client = client();
		assertThat(client.beginBridgeDisconnect()).isTrue();
		AtomicBoolean ran = new AtomicBoolean();
		assertThat(client.bridgePublish(() -> ran.set(true))).isFalse();
		assertThat(ran).isFalse();
		assertThat(client.capturedBridgePlayer()).isNull();
	}
	
	@Test
	void aPublicationIsVisibleToTheDisconnectThatFollowsIt() throws Exception
	{
		L2Client client = client();
		L2Player player = HuntTestPlayers.mockPlayer();
		assertThat(client.bridgePublish(() ->
		{
			client.noteBridgeOpeningPlayer(player);
			client.setActiveChar(player);
		})).isTrue();
		assertThat(client.beginBridgeDisconnect()).isTrue();
		assertThat(client.capturedBridgePlayer()).isSameAs(player);
	}
	
	private static L2Client client() throws Exception
	{
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		Channel channel = mock(Channel.class);
		org.mockito.Mockito.when(channel.remoteAddress()).thenReturn(new java.net.InetSocketAddress("127.0.0.1", 1));
		ClientSink sink = new ClientSink() {
			@Override
			public boolean isBridge()
			{
				return true;
			}
			
			@Override
			public void deliver(L2ServerPacket packet, L2Player player)
			{
			}
			
			@Override
			public void disconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure failure)
			{
			}
			
			@Override
			public void ownTeleported(long generation)
			{
			}
		};
		return L2Client.forBridge(server, channel, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
	}
}
