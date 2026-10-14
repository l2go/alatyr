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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.util.Util;

class HuntRecipientTest
{
	@Test
	void deadGroupWithDominanceDoesNotDeliverItsItem()
	{
		checkUnavailableSide(true, true, true);
	}

	@Test
	void deadSoloReceivesNothing()
	{
		checkUnavailableSide(false, true, true);
	}

	@Test
	void nearbyLivingSoloDoesNotInheritTheFarSolosShare()
	{
		checkUnavailableSide(false, false, false);
	}

	private static void checkUnavailableSide(boolean grouped, boolean dead, boolean nearby)
	{
		L2Attackable mob = mock(L2Attackable.class);
		L2Player absent = HuntTestPlayers.mockPlayer();
		L2Player present = HuntTestPlayers.mockPlayer();
		L2NpcTemplate template = mock(L2NpcTemplate.class);
		when(absent.getObjectId()).thenReturn(1);
		when(present.getObjectId()).thenReturn(2);
		when(absent.getActingPlayer()).thenReturn(absent);
		when(present.getActingPlayer()).thenReturn(present);
		when(absent.getLevel()).thenReturn(10);
		when(present.getLevel()).thenReturn(10);
		when(absent.isDead()).thenReturn(dead);
		when(present.calcStat(eq(Stats.EXPSP_RATE), eq(1.0), isNull(), isNull())).thenReturn(1.0);
		when(mob.getLevel()).thenReturn(10);
		when(mob.getExpReward()).thenReturn(88);
		when(mob.getSpReward()).thenReturn(8);
		when(mob.getMustRewardExpSP()).thenReturn(true);
		when(mob.getTemplate()).thenReturn(template);
		when(mob.usesHuntRewards()).thenReturn(true);
		doCallRealMethod().when(mob).huntContributions();
		doCallRealMethod().when(mob).recordHuntDamage(any(), anyDouble(), anyDouble());
		doCallRealMethod().when(mob).calculateRewards(any());
		ReentrantLock coinLock = spy(new ReentrantLock());
		if (grouped)
		{
			L2Party party = mock(L2Party.class);
			when(party.getPartyMembers()).thenReturn(List.of(absent));
			when(party.getCoinDistributionLock()).thenReturn(coinLock);
			when(absent.getParty()).thenReturn(party);
		}
		doAnswer(call -> {
			List<L2Attackable.RewardItem> drops = call.getArgument(2);
			drops.add(new L2Attackable.RewardItem(57, 12));
			drops.add(new L2Attackable.RewardItem(60001, 1));
			return null;
		}).when(mob).doItemDrop(eq(template), eq(present), anyList());
		try (MockedStatic<Util> range = mockStatic(Util.class); MockedStatic<ItemTable> tables = mockStatic(ItemTable.class))
		{
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, absent, true)).thenReturn(nearby);
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, present, true)).thenReturn(true);
			ItemTable table = mock(ItemTable.class);
			L2Item item = mock(L2Item.class);
			when(item.isStackable()).thenReturn(true);
			when(table.getTemplate(anyInt())).thenReturn(item);
			tables.when(ItemTable::getInstance).thenReturn(table);
			mob.recordHuntDamage(absent, 750, 1000);
			mob.recordHuntDamage(present, 250, 250);
			mob.calculateRewards(present);
			verify(present).addExpAndSp(22, 2, false);
			verify(present).addAdena("Hunt", 3, mob, true);
			verify(absent, never()).addExpAndSp(anyLong(), anyInt(), anyBoolean());
			verify(absent, never()).addAdena(anyString(), anyLong(), any(), anyBoolean());
			verify(table, never()).createItem(anyString(), anyInt(), anyLong(), any(), any());
			verify(mob, never()).dropItem(any(L2Player.class), anyInt(), anyInt());
			if (grouped)
			{
				org.mockito.InOrder order = inOrder(absent, coinLock);
				order.verify(absent).isDead();
				order.verify(coinLock).lock();
			}
		}
	}
}
