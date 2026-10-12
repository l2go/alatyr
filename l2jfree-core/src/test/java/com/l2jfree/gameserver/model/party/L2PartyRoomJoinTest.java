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
package com.l2jfree.gameserver.model.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance;
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

class L2PartyRoomJoinTest
{
	private final List<L2Player> _players = new ArrayList<L2Player>();
	private final List<AtomicBoolean> _looking = new ArrayList<AtomicBoolean>();
	
	@AfterEach
	void leaveTheWaitingList()
	{
		for (L2Player player : _players)
			PartyRoomManager.getInstance().removeFromWaitingList(player);
	}
	
	@Test
	@DisplayName("a full room leaves the refused player on the waiting list")
	void fullRoomKeepsTheWaitingPlayer()
	{
		L2PartyRoom room = new L2PartyRoom(900_001, 1, 85, 2, 0, "room");
		L2Player leader = player();
		L2Player taken = player();
		L2Player refused = player();
		PartyRoomManager.getInstance().addToWaitingList(taken);
		PartyRoomManager.getInstance().addToWaitingList(refused);
		
		assertThat(room.addMember(leader)).isTrue();
		assertThat(room.addMember(taken)).isTrue();
		assertThat(looking(taken)).isFalse();
		assertThat(room.addMember(refused)).isFalse();
		assertThat(looking(refused)).isTrue();
		assertThat(room.addMember(taken)).isFalse();
		assertThat(looking(taken)).isFalse();
		
		List<L2Player> snapshot = room.getMembers();
		snapshot.clear();
		assertThat(room.getMemberCount()).isEqualTo(2);
		assertThat(room.getMembers()).containsExactly(leader, taken);
	}
	
	@Test
	@DisplayName("one free seat is given to one of two players who join together")
	void oneSeatAdmitsOneWaitingPlayer() throws Exception
	{
		L2PartyRoom room = new L2PartyRoom(900_002, 1, 85, 2, 0, "room");
		assertThat(room.addMember(player())).isTrue();
		L2Player first = player();
		L2Player second = player();
		PartyRoomManager.getInstance().addToWaitingList(first);
		PartyRoomManager.getInstance().addToWaitingList(second);
		CyclicBarrier start = new CyclicBarrier(2);
		boolean[] joined = new boolean[2];
		Thread[] threads = new Thread[2];
		L2Player[] players = { first, second };
		for (int i = 0; i < 2; i++)
		{
			int index = i;
			threads[i] = new Thread(() -> {
				try
				{
					start.await();
					joined[index] = L2PartyRoom.tryJoin(players[index], room, true);
				}
				catch (Exception e)
				{
					throw new IllegalStateException(e);
				}
			});
			threads[i].start();
		}
		for (Thread thread : threads)
			thread.join();
		
		assertThat(joined[0] ^ joined[1]).isTrue();
		assertThat(looking(joined[0] ? second : first)).isTrue();
		assertThat(looking(joined[0] ? first : second)).isFalse();
		assertThat(room.getMemberCount()).isEqualTo(2);
	}
	
	@Test
	@DisplayName("joining and leaving while the room is broadcast does not throw")
	void concurrentJoinAndBroadcast() throws Exception
	{
		L2PartyRoom room = new L2PartyRoom(900_003, 1, 85, 12, 0, "room");
		assertThat(room.addMember(player())).isTrue();
		List<L2Player> extras = new ArrayList<L2Player>();
		for (int i = 0; i < 8; i++)
			extras.add(player());
		L2ServerPacket packet = mock(L2ServerPacket.class);
		AtomicBoolean run = new AtomicBoolean(true);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		Thread broadcaster = new Thread(() -> {
			try
			{
				while (run.get())
				{
					room.broadcastPacket(packet);
					for (L2Player member : room.getMembers())
						member.getLevel();
				}
			}
			catch (Throwable thrown)
			{
				failure.set(thrown);
			}
		});
		broadcaster.start();
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try
		{
			for (int thread = 0; thread < 4; thread++)
			{
				int offset = thread;
				pool.submit(() -> {
					try
					{
						for (int i = 0; i < 200; i++)
						{
							L2Player extra = extras.get((offset + i) % extras.size());
							room.addMember(extra);
							room.removeMember(extra, false);
						}
					}
					catch (Throwable thrown)
					{
						failure.set(thrown);
					}
				});
			}
		}
		finally
		{
			pool.shutdown();
		}
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		run.set(false);
		broadcaster.join(5_000);
		assertThat(broadcaster.isAlive()).isFalse();
		assertThat(failure.get()).isNull();
		assertThat(room.getMemberCount()).isGreaterThanOrEqualTo(1);
	}
	
	private L2Player player()
	{
		L2Player player = mock(L2Player.class);
		PlayerAppearance appearance = mock(PlayerAppearance.class);
		when(appearance.getVisibleName()).thenReturn("pc");
		when(player.getAppearance()).thenReturn(appearance);
		when(player.getLevel()).thenReturn(40);
		AtomicBoolean flag = new AtomicBoolean(false);
		doAnswer(invocation -> {
			flag.set(invocation.getArgument(0));
			return null;
		}).when(player).setLookingForParty(anyBoolean());
		AtomicReference<L2PartyRoom> joined = new AtomicReference<L2PartyRoom>();
		doAnswer(invocation -> {
			joined.set(invocation.getArgument(0));
			return null;
		}).when(player).setPartyRoom(nullable(L2PartyRoom.class));
		when(player.getPartyRoom()).thenAnswer(invocation -> joined.get());
		_players.add(player);
		_looking.add(flag);
		return player;
	}
	
	private boolean looking(L2Player player)
	{
		return _looking.get(_players.indexOf(player)).get();
	}
}
