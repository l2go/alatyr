/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.network.NetworkServer;

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
		var client = mock(L2Client.class);
		when(client.getActiveChar()).thenReturn(mock(L2Player.class));
		when(client.getNetworkServer()).thenReturn(mock(NetworkServer.class));
		doThrow(error ? new AssertionError("entry fixture") : new IllegalStateException("entry fixture"))
				.when(client).initServerPacketQueue();
		assertThat(BridgeAdmission.enterWorld(client)).isFalse();
		verify(client).requestBridgeDisconnect(BridgeFailure.internal());
		verify(client, never()).requestBridgeDisconnect();
	}
}
