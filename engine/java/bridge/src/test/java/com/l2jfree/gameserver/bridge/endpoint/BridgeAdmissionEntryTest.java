/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.bridge.ClientSink;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.FloodManager.ErrorMode;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.Channel;

class BridgeAdmissionEntryTest
{
	@Test
	void closingBeforeEntryDoesNotStartTheWorldHandler()
	{
		var client = mock(L2Client.class);
		when(client.isBridgeDisconnectRequested()).thenReturn(true);
		assertThat(BridgeAdmission.enterWorld(client)).isFalse();
		verify(client, never()).initServerPacketQueue();
		verify(client, never()).getActiveChar();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
	@SuppressWarnings("unchecked")
	void aFailedEntryRequestsTheFullInternalDisconnect(boolean error)
	{
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		var sink = mock(ClientSink.class);
		when(sink.isBridge()).thenReturn(true);
		var client = spy(L2Client.forBridge(server, mock(Channel.class), InetAddress.getLoopbackAddress(), sink));
		client.setActiveChar(mock(L2Player.class));
		doNothing().when(client).requestBridgeDisconnect(any(BridgeFailure.class));
		Throwable failure = error ? new AssertionError("entry fixture") : new IllegalStateException("entry fixture");
		doThrow(failure).when(client).initServerPacketQueue();
		assertThat(BridgeAdmission.enterWorld(client)).isFalse();
		verify(client).initServerPacketQueue();
		verify(client).requestBridgeDisconnect(BridgeFailure.internal());
		verify(client, never()).requestBridgeDisconnect();
		if (error)
			verifyNoInteractions(server);
		else
			verify(server).report(eq(ErrorMode.FAILED_RUNNING), same(client), any(L2ClientPacket.class), same(failure));
	}
}
