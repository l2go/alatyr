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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.bridge.ClientSink;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.Channel;

/**
 * An ordinary client keeps its cipher. A bridge client has none, and a second disconnect request does not start again.
 */
class BridgeClientTest
{
	@Test
	void anOrdinaryClientKeepsItsCipherBesideABridgeClient() throws Exception
	{
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		L2Client ordinary = new L2Client(server, addressedChannel());
		AtomicInteger byes = new AtomicInteger();
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
			public void disconnect(BridgeFailure failure)
			{
				byes.incrementAndGet();
			}
			
			@Override
			public void ownTeleported(long generation)
			{
			}
		};
		L2Client bridge = L2Client.forBridge(server, addressedChannel(),
				InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		
		assertThat(ordinary.isBridge()).isFalse();
		assertThat(bridge.isBridge()).isTrue();
		assertThat(ordinary.enableCrypt()).isNotEmpty();
		assertThatThrownBy(bridge::enableCrypt).isInstanceOf(IllegalStateException.class);
		assertThat(byes).hasValue(0);
	}
	
	@Test
	void aSecondDisconnectRequestDoesNotStartAgain() throws Exception
	{
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
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
			public void disconnect(BridgeFailure failure)
			{
			}
			
			@Override
			public void ownTeleported(long generation)
			{
			}
		};
		L2Client bridge = L2Client.forBridge(server, addressedChannel(),
				InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		L2Client ordinary = new L2Client(server, addressedChannel());
		
		assertThat(bridge.beginBridgeDisconnect()).isTrue();
		assertThat(bridge.beginBridgeDisconnect()).isFalse();
		assertThat(bridge.isBridgeDisconnectRequested()).isTrue();
		assertThat(ordinary.beginBridgeDisconnect()).isFalse();
	}
	
	private static Channel addressedChannel()
	{
		Channel channel = mock(Channel.class);
		when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 2106));
		return channel;
	}
}
