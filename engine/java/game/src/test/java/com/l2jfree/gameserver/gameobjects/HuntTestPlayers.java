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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

import com.l2jfree.gameserver.datatables.SkillTreeTable;

/** Keeps the player's static expertise lookup from opening a database in a unit test. */
public final class HuntTestPlayers
{
	private HuntTestPlayers()
	{
	}

	public static L2Player mockPlayer()
	{
		try (MockedStatic<SkillTreeTable> tables = mockStatic(SkillTreeTable.class))
		{
			SkillTreeTable tree = mock(SkillTreeTable.class);
			when(tree.getExpertiseLevel(anyInt())).thenReturn(-1);
			when(tree.getExpertiseLevel(0)).thenReturn(0);
			tables.when(SkillTreeTable::getInstance).thenReturn(tree);
			return mock(L2Player.class);
		}
	}
}
