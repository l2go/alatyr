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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.HuntTestPlayers;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.bridge.ClientSink;

/**
 * A failed save is an internal bye, not a normal one.
 */
class BridgeStoreFailureTest
{
	@Test
	void aFailedSaveIsNotANormalBye()
	{
		L2Client client = mock(L2Client.class);
		L2Player player = HuntTestPlayers.mockPlayer();
		when(client.capturedBridgePlayer()).thenReturn(player);
		when(client.capturedBridgeAccount()).thenReturn("");
		when(player.store(true, true)).thenThrow(new RuntimeException("disk"));
		ClientSink sink = mock(ClientSink.class);
		when(client.sink()).thenReturn(sink);
		
		DisconnectionExecutor.full(client);
		
		verify(player).store(true, true);
		verify(sink).disconnect(eq(BridgeFailure.internal()));
		verify(sink, org.mockito.Mockito.never()).disconnect(eq(BridgeFailure.normal()));
	}
}
