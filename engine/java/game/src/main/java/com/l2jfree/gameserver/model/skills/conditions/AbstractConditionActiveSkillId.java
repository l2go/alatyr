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
package com.l2jfree.gameserver.model.skills.conditions;

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.L2Skill;

abstract class AbstractConditionActiveSkillId extends Condition
{
	private final int _skillId;
	private final int _minSkillLvl;
	private final int _maxSkillLvl;
	
	AbstractConditionActiveSkillId(String nodeValue)
	{
		final StringTokenizer st = new StringTokenizer(nodeValue, ",");
		
		_skillId = Integer.decode(st.nextToken());
		_minSkillLvl = st.hasMoreTokens() ? Integer.decode(st.nextToken()) : Integer.MIN_VALUE;
		_maxSkillLvl = st.hasMoreTokens() ? Integer.decode(st.nextToken()) : Integer.MAX_VALUE;
	}
	
	@Override
	boolean testImpl(Env env)
	{
		final L2Creature owner = getSkillOwner(env);
		
		if (owner != null)
			for (L2Skill sk : owner.getAllSkills())
				if (sk != null && sk.getId() == _skillId)
					if (_minSkillLvl <= sk.getLevel() && sk.getLevel() <= _maxSkillLvl)
						return true;
		
		return false;
	}
	
	abstract L2Creature getSkillOwner(Env env);
}
