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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.model.drop.L2DropCategory;
import com.l2jfree.gameserver.model.drop.L2DropData;

class HuntDropLevelModifierTest
{
	@Test
	void deepBlueModifierIsPassedToBothLegacyAndHuntDropPaths()
	{
		boolean previous = Config.DEEPBLUE_DROP_RULES;
		Config.DEEPBLUE_DROP_RULES = true;
		try (MockedStatic<CursedWeaponsManager> weapons = mockStatic(CursedWeaponsManager.class))
		{
			weapons.when(CursedWeaponsManager::getInstance).thenReturn(mock(CursedWeaponsManager.class));
			L2Attackable mob = mock(L2Attackable.class);
			L2Player player = HuntTestPlayers.mockPlayer();
			L2NpcTemplate template = mock(L2NpcTemplate.class);
			L2DropCategory category = mock(L2DropCategory.class);
			when(player.getActingPlayer()).thenReturn(player);
			when(player.getLevel()).thenReturn(20);
			when(mob.getLevel()).thenReturn(10);
			when(mob.getTemplate()).thenReturn(template);
			when(template.getDropData()).thenReturn(new L2DropCategory[] { category });
			doCallRealMethod().when(mob).calculateLevelModifierForDrop(player);
			doCallRealMethod().when(mob).doItemDrop(template, player);
			doCallRealMethod().when(mob).doItemDrop(eq(template), eq(player), nullable(List.class));
			assertThat(mob.calculateLevelModifierForDrop(player)).isEqualTo(18);

			mob.doItemDrop(template, player);
			mob.doItemDrop(template, player, new ArrayList<>());
			verify(mob, times(2)).calculateCategorizedRewardItem(player, category, 18);

			L2DropData seeded = mock(L2DropData.class);
			when(mob.isSeeded()).thenReturn(true);
			when(category.dropSeedAllowedDropsOnly()).thenReturn(seeded);
			mob.doItemDrop(template, player);
			mob.doItemDrop(template, player, new ArrayList<>());
			verify(mob, times(2)).calculateRewardItem(player, seeded, 18, false);
		}
		finally
		{
			Config.DEEPBLUE_DROP_RULES = previous;
		}
	}
}
