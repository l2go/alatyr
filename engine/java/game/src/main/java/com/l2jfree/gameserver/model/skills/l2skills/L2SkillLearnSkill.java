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
package com.l2jfree.gameserver.model.skills.l2skills;

import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;

public class L2SkillLearnSkill extends L2Skill
{
	private final int[] _learnSkillId;
	private final int[] _learnSkillLvl;
	
	public L2SkillLearnSkill(StatsSet set)
	{
		super(set);
		
		String[] ar = set.getString("learnSkillId", "0").split(",");
		int[] ar2 = new int[ar.length];
		
		for (int i = 0; i < ar.length; i++)
			ar2[i] = Integer.parseInt(ar[i]);
		
		_learnSkillId = ar2;
		
		ar = set.getString("learnSkillLvl", "1").split(",");
		ar2 = new int[_learnSkillId.length];
		
		for (int i = 0; i < _learnSkillId.length; i++)
			ar2[i] = 1;
		
		for (int i = 0; i < ar.length; i++)
			ar2[i] = Integer.parseInt(ar[i]);
		
		_learnSkillLvl = ar2;
	}
	
	/**
	 * used for learning skills through skills
	 * 
	 * @return new skill id to learn (if not defined, default 0)
	 */
	public int[] getNewSkillId()
	{
		return _learnSkillId;
	}
	
	/**
	 * used for learning skills through skills
	 * 
	 * @return skill lvl to learn (if not defined, default 1)
	 */
	public int[] getNewSkillLvl()
	{
		return _learnSkillLvl;
	}
}
