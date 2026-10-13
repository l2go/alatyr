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
package com.l2jfree.gameserver.datatables;

import java.util.Map;

import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;

/**
 * Expertise (skill {@value L2Skill#SKILL_EXPERTISE}) as the skill tree defines it. A world with no such rows has no
 * expertise: the grade is absent, and the skill is not granted.
 */
public final class Expertise
{
	private Expertise()
	{
	}
	
	/**
	 * @param tree the skill tree expertise is read from, or null when that class has none
	 * @param grade the expertise grade, 0 for none
	 * @return the minimum player level of the grade, or -1 when the world does not define it
	 */
	public static int minimumLevel(Map<Integer, L2SkillLearn> tree, int grade)
	{
		if (grade <= 0)
			return 0;
		if (tree == null)
			return -1;
		
		L2SkillLearn learn = tree.get(SkillTable.getSkillUID(L2Skill.SKILL_EXPERTISE, grade));
		return learn == null ? -1 : learn.getMinLevel();
	}
	
	/**
	 * @param playerLevel the level of the player
	 * @param minimumLevels one entry per grade; a negative entry means that grade is not defined
	 * @return the expertise skill level to grant, or 0 when no defined grade has been reached
	 */
	public static int skillLevel(int playerLevel, int[] minimumLevels)
	{
		int index = 0;
		for (int i = 0; i < minimumLevels.length; i++)
		{
			if (minimumLevels[i] >= 0 && playerLevel >= minimumLevels[i])
				index = i;
		}
		return index;
	}
}
