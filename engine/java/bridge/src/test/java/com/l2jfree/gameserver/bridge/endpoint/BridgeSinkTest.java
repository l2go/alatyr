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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.embedded.EmbeddedChannel;

/**
 * The sink does not write a server packet, and admission is not run on the caller.
 */
class BridgeSinkTest
{
	@Test
	void deliverAndDisconnectDoNotWriteTheVirtualChannel() throws Exception
	{
		EmbeddedChannel channel = new EmbeddedChannel();
		List<List<Envelope>> written = new ArrayList<>();
		BridgeSink sink = new BridgeSink(written::add);
		@SuppressWarnings("unchecked")
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		L2Client client = L2Client.forBridge(server, channel, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
		sink.attach(client);
		
		sink.deliver(com.l2jfree.gameserver.network.packets.server.ActionFailed.STATIC_PACKET, null);
		sink.disconnect(BridgeFailure.normal());
		sink.disconnect(BridgeFailure.internal());
		
		assertThat(channel.outboundMessages()).isNullOrEmpty();
		assertThat(written).hasSize(1);
		assertThat(written.get(0).get(0).field(1).value().u()).isZero();
	}
	
	@Test
	void aTeleportCompletionIsOnlyQueued()
	{
		L2Client client = mock(L2Client.class);
		when(client.isBridge()).thenReturn(true);
		AtomicReference<Runnable> queued = new AtomicReference<>();
		doAnswer(invocation ->
		{
			queued.set(invocation.getArgument(0));
			return null;
		}).when(client).submit(any(Runnable.class));
		BridgeSink sink = new BridgeSink(records ->
		{
		});
		sink.attach(client);
		
		sink.ownTeleported(4);
		
		verify(client, never()).getActiveChar();
		assertThat(queued.get()).isNotNull();
		when(client.getActiveChar()).thenReturn(null);
		queued.get().run();
		verify(client).getActiveChar();
	}
	
	@Test
	void scheduleLeavesAdmissionOnTheClientQueue()
	{
		L2Client client = mock(L2Client.class);
		LoginLink login = mock(LoginLink.class);
		AtomicReference<Runnable> queued = new AtomicReference<>();
		doAnswer(invocation ->
		{
			queued.set(invocation.getArgument(0));
			return null;
		}).when(client).submit(any(Runnable.class));
		
		BridgeAdmission.schedule(client, login, "guest", "Guest", records ->
		{
		});
		
		verify(login, never()).admitTrusted(anyString(), any());
		assertThat(queued.get()).isNotNull();
	}
}
