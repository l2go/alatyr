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
import com.l2jfree.gameserver.model.items.L2ItemInstance;
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

	@Test
	void solePartyPaysTheDeathSnapshotImmediately()
	{
		// Both members are alive and in range when the beast dies. The third is
		// already listed in the party but out of range, as someone invited after
		// the kill would be. Coins are paid at once, so the share does not depend
		// on who is still nearby when the pile is picked up.
		L2Attackable mob = mock(L2Attackable.class);
		L2Player stayer = HuntTestPlayers.mockPlayer();
		L2Player leaver = HuntTestPlayers.mockPlayer();
		L2Player invited = HuntTestPlayers.mockPlayer();
		L2NpcTemplate template = mock(L2NpcTemplate.class);
		when(stayer.getObjectId()).thenReturn(1);
		when(leaver.getObjectId()).thenReturn(2);
		when(invited.getObjectId()).thenReturn(3);
		when(stayer.getActingPlayer()).thenReturn(stayer);
		when(leaver.getActingPlayer()).thenReturn(leaver);
		when(invited.getActingPlayer()).thenReturn(invited);
		when(stayer.getLevel()).thenReturn(10);
		when(leaver.getLevel()).thenReturn(10);
		when(invited.getLevel()).thenReturn(10);
		when(stayer.calcStat(eq(Stats.EXPSP_RATE), eq(1.0), isNull(), isNull())).thenReturn(1.0);
		when(leaver.calcStat(eq(Stats.EXPSP_RATE), eq(1.0), isNull(), isNull())).thenReturn(1.0);
		when(mob.getLevel()).thenReturn(10);
		when(mob.getExpReward()).thenReturn(88);
		when(mob.getSpReward()).thenReturn(8);
		when(mob.getMustRewardExpSP()).thenReturn(true);
		when(mob.getTemplate()).thenReturn(template);
		when(mob.usesHuntRewards()).thenReturn(true);
		doCallRealMethod().when(mob).huntContributions();
		doCallRealMethod().when(mob).recordHuntDamage(any(), anyDouble(), anyDouble());
		doCallRealMethod().when(mob).calculateRewards(any());
		L2Party party = mock(L2Party.class);
		when(party.getPartyMembers()).thenReturn(List.of(stayer, leaver, invited));
		when(party.getCoinDistributionLock()).thenReturn(new ReentrantLock());
		when(party.getCoinCursor()).thenReturn(0);
		when(party.getHuntLooter(any(), anyInt(), any(), any())).thenAnswer(call -> {
			if (!List.of(stayer, leaver).equals(call.getArgument(3)))
				throw new AssertionError("item recipients " + call.getArgument(3));
			return leaver;
		});
		when(stayer.getParty()).thenReturn(party);
		when(leaver.getParty()).thenReturn(party);
		when(invited.getParty()).thenReturn(party);
		doAnswer(call -> {
			List<L2Attackable.RewardItem> drops = call.getArgument(2);
			drops.add(new L2Attackable.RewardItem(57, 13));
			drops.add(new L2Attackable.RewardItem(60001, 1));
			return null;
		}).when(mob).doItemDrop(eq(template), eq(stayer), anyList());
		try (MockedStatic<Util> range = mockStatic(Util.class); MockedStatic<ItemTable> tables = mockStatic(ItemTable.class))
		{
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, stayer, true)).thenReturn(true);
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, leaver, true)).thenReturn(true);
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, invited, true)).thenReturn(false);
			ItemTable table = mock(ItemTable.class);
			L2Item templateItem = mock(L2Item.class);
			L2ItemInstance dropped = mock(L2ItemInstance.class);
			when(templateItem.isStackable()).thenReturn(true);
			when(table.getTemplate(anyInt())).thenReturn(templateItem);
			when(table.createItem(eq("Hunt"), eq(60001), eq(1L), eq(leaver), eq(mob))).thenReturn(dropped);
			tables.when(ItemTable::getInstance).thenReturn(table);
			mob.recordHuntDamage(stayer, 500, 1000);
			mob.recordHuntDamage(leaver, 500, 500);
			mob.calculateRewards(stayer);
			verify(stayer).addAdena("Hunt", 7, mob, true);
			verify(leaver).addAdena("Hunt", 6, mob, true);
			verify(party).setCoinCursor(1);
			verify(dropped).setHuntRewardOwner(2);
			verify(invited, never()).addAdena(anyString(), anyLong(), any(), anyBoolean());
			verify(invited, never()).addExpAndSp(anyLong(), anyInt(), anyBoolean());
			verify(mob, never()).dropItem(any(L2Player.class), anyInt(), anyInt());
		}
	}

	@Test
	void lonePlayerStillDropsOnTheGround()
	{
		L2Attackable mob = mock(L2Attackable.class);
		L2Player player = HuntTestPlayers.mockPlayer();
		L2NpcTemplate template = mock(L2NpcTemplate.class);
		when(player.getObjectId()).thenReturn(1);
		when(player.getActingPlayer()).thenReturn(player);
		when(player.getLevel()).thenReturn(10);
		when(player.calcStat(eq(Stats.EXPSP_RATE), eq(1.0), isNull(), isNull())).thenReturn(1.0);
		when(mob.getLevel()).thenReturn(10);
		when(mob.getExpReward()).thenReturn(88);
		when(mob.getSpReward()).thenReturn(8);
		when(mob.getMustRewardExpSP()).thenReturn(true);
		when(mob.getTemplate()).thenReturn(template);
		when(mob.usesHuntRewards()).thenReturn(true);
		doCallRealMethod().when(mob).huntContributions();
		doCallRealMethod().when(mob).recordHuntDamage(any(), anyDouble(), anyDouble());
		doCallRealMethod().when(mob).calculateRewards(any());
		doAnswer(call -> {
			List<L2Attackable.RewardItem> drops = call.getArgument(2);
			drops.add(new L2Attackable.RewardItem(57, 12));
			drops.add(new L2Attackable.RewardItem(60001, 1));
			return null;
		}).when(mob).doItemDrop(eq(template), eq(player), anyList());
		try (MockedStatic<Util> range = mockStatic(Util.class); MockedStatic<ItemTable> tables = mockStatic(ItemTable.class))
		{
			range.when(() -> Util.checkIfInRange(Config.ALT_PARTY_RANGE, mob, player, true)).thenReturn(true);
			ItemTable table = mock(ItemTable.class);
			L2Item item = mock(L2Item.class);
			when(item.isStackable()).thenReturn(true);
			when(table.getTemplate(anyInt())).thenReturn(item);
			tables.when(ItemTable::getInstance).thenReturn(table);
			mob.recordHuntDamage(player, 1000, 1000);
			mob.calculateRewards(player);
			verify(player).addExpAndSp(88, 8, false);
			verify(player, never()).addAdena(anyString(), anyLong(), any(), anyBoolean());
			verify(mob).dropItem(player, 57, 12);
			verify(mob).dropItem(player, 60001, 1);
			verify(table, never()).createItem(anyString(), anyInt(), anyLong(), any(), any());
		}
	}
}
