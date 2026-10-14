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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.skills.L2Skill;

class AttackableHuntTest
{
	private static L2Attackable mob()
	{
		L2Attackable mob = mock(L2Attackable.class);
		when(mob.usesHuntRewards()).thenReturn(true);
		doCallRealMethod().when(mob).huntContributions();
		doCallRealMethod().when(mob).recordHuntDamage(any(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
		return mob;
	}

	private static L2Player player(int id)
	{
		L2Player player = mock(L2Player.class);
		when(player.getObjectId()).thenReturn(id);
		when(player.getActingPlayer()).thenReturn(player);
		return player;
	}

	@Test
	void twoActualHpHitsAccumulateWithoutChangingHate()
	{
		L2Attackable mob = mob();
		L2Player player = player(1);
		mob.recordHuntDamage(player, 200, 1000);
		mob.recordHuntDamage(player, 300, 800);
		assertThat(mob.huntContributions().damage()).containsEntry(1L, 500.0);
		verify(mob, never()).addDamageHate(any(), anyInt(), anyInt());
		verify(mob, never()).getAggroListRP();
	}

	@Test
	void assistNotificationsCannotCreateHpCredit()
	{
		L2Attackable mob = mob();
		L2Player player = player(1);
		when(mob.getTemplate()).thenReturn(mock(L2NpcTemplate.class));
		doCallRealMethod().when(mob).addDamage(any(), anyInt(), any(L2Skill.class));
		doCallRealMethod().when(mob).addDamage(player, 100, null);
		mob.addDamage(player, 100, null);
		assertThat(mob.huntContributions().damage()).isEmpty();
		verify(mob, never()).addDamageHate(any(), anyInt(), anyInt());
	}

	@Test
	void summonDamageBelongsToItsOwnerAndPartyMembershipStaysFixed()
	{
		L2Attackable mob = mob();
		L2Player player = player(1);
		L2Summon summon = mock(L2Summon.class);
		when(summon.getActingPlayer()).thenReturn(player);
		L2Party party = mock(L2Party.class);
		when(player.getParty()).thenReturn(party);
		mob.recordHuntDamage(summon, 200, 1000);
		when(player.getParty()).thenReturn(null);
		mob.recordHuntDamage(player, 300, 800);
		assertThat(mob.huntContributions().damage()).containsOnlyKeys(-1L).containsEntry(-1L, 500.0);
	}

	@Test
	void guardAndEnvironmentShareOneNeutralSide()
	{
		L2Attackable mob = mob();
		mob.recordHuntDamage(mock(L2Creature.class), 100, 1000);
		mob.recordHuntDamage(null, 200, 900);
		assertThat(mob.huntContributions().damage()).containsOnlyKeys(0L).containsEntry(0L, 300.0);
	}
}
