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
package com.l2jfree.gameserver.gameobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.position.ObjectPosition;
import com.l2jfree.gameserver.network.L2Client;

/**
 * A teleport that starts while a bridge completion holds the lock is queued, not dropped.
 */
class BridgeTeleportDeferralTest
{
	@Test
	void aTeleportDuringCompletionRunsAfterTheCompletionInOrder()
	{
		Probe probe = new Probe();
		probe.player._bridgeCompleting = true;
		
		probe.player.teleToLocation(11, 22, 33, 7, false);
		probe.player.teleToLocation(44, 55, 66, 0, false);
		
		assertThat(probe.player._teleportGeneration).isZero();
		assertThat(probe.queued).hasSize(2);
		verify(probe.client, never()).publishOwnTeleport(anyLong());
		
		probe.player._bridgeCompleting = false;
		probe.queued.get(0).run();
		probe.queued.get(1).run();
		
		assertThat(probe.player._teleportGeneration).isEqualTo(2L);
		assertThat(probe.queued).hasSize(2);
		InOrder positions = inOrder(probe.position);
		positions.verify(probe.position).setWorldPosition(11, 22, 38);
		positions.verify(probe.position).setHeading(7);
		positions.verify(probe.position).setWorldPosition(44, 55, 71);
		verify(probe.client).publishOwnTeleport(1L);
		verify(probe.client).publishOwnTeleport(2L);
	}
	
	@Test
	void aTeleportDuringCompletionIsNotQueuedWithoutAClient()
	{
		Probe probe = new Probe();
		when(probe.player.getClient()).thenReturn(null);
		probe.player._bridgeCompleting = true;
		
		probe.player.teleToLocation(1, 2, 3, 4, false);
		
		assertThat(probe.player._teleportGeneration).isZero();
		assertThat(probe.queued).isEmpty();
	}
	
	@Test
	void aTeleportDuringCompletionIsNotQueuedWhenClosing()
	{
		Probe probe = new Probe();
		when(probe.player.isBridgeClosing()).thenReturn(true);
		probe.player._bridgeCompleting = true;
		
		probe.player.teleToLocation(1, 2, 3, 4, false);
		
		assertThat(probe.player._teleportGeneration).isZero();
		verify(probe.client, never()).submit(any());
	}
	
	@Test
	void aTeleportDuringCompletionIsNotQueuedAfterDisconnect()
	{
		Probe probe = new Probe();
		when(probe.client.isBridgeDisconnectRequested()).thenReturn(true);
		probe.player._bridgeCompleting = true;
		
		probe.player.teleToLocation(1, 2, 3, 4, false);
		
		assertThat(probe.player._teleportGeneration).isZero();
		verify(probe.client, never()).submit(any());
	}
	
	private static final class Probe
	{
		final L2Player player = mock(L2Player.class);
		final L2Client client = mock(L2Client.class);
		final ObjectPosition position = mock(ObjectPosition.class);
		final List<Runnable> queued = new ArrayList<>();
		
		Probe()
		{
			player._teleportLock = new ReentrantLock();
			when(player.isBridgeSession()).thenReturn(true);
			when(player.getClient()).thenReturn(client);
			when(player.getAI()).thenReturn(mock(L2CreatureAI.class));
			when(player.getObjectId()).thenReturn(1);
			when(player.getPosition()).thenReturn(position);
			doAnswer(invocation ->
			{
				queued.add(invocation.getArgument(0));
				return null;
			}).when(client).submit(any(Runnable.class));
			doCallRealMethod().when(player).teleToLocation(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
		}
	}
}
