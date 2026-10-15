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
package com.l2jfree.gameserver.handler;

import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;

/**
 * @author NB4L1
 */
public interface ISkillTargetHandler
{
	public List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill,
			L2Creature target);
	
	public SkillTargetType[] getSkillTargetTypes();
}
