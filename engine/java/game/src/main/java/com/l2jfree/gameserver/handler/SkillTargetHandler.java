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
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.util.EnumHandlerRegistry;

/**
 * @author NB4L1
 */
public class SkillTargetHandler extends EnumHandlerRegistry<SkillTargetType, ISkillTargetHandler>
{
	private static final class SingletonHolder
	{
		private static final SkillTargetHandler INSTANCE = new SkillTargetHandler();
	}
	
	public static SkillTargetHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private SkillTargetHandler()
	{
		super(SkillTargetType.class);
		
		_log.info("SkillTargetHandler: Loaded " + size() + " handlers.");
	}
	
	public void registerSkillHandler(ISkillTargetHandler skillTargetList)
	{
		registerAll(skillTargetList, skillTargetList.getSkillTargetTypes());
	}
	
	public List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill,
			L2Creature target)
	{
		final List<L2Creature> targets = GlobalRestrictions.getTargetList(type, activeChar, skill, target);
		
		if (targets != null)
			return targets;
		
		final ISkillTargetHandler list = get(type);
		
		if (list != null)
			return list.getTargetList(type, activeChar, skill, target);
		
		return null;
	}
	
	public List<L2Creature> getTargetList(SkillTargetType type, L2Creature activeChar, L2Skill skill)
	{
		return getTargetList(type, activeChar, skill, activeChar.getTarget(L2Creature.class));
	}
	
	public List<L2Creature> getTargetList(L2Creature activeChar, L2Skill skill, L2Creature target)
	{
		return getTargetList(skill.getTargetType(), activeChar, skill, target);
	}
	
	public List<L2Creature> getTargetList(L2Creature activeChar, L2Skill skill)
	{
		return getTargetList(skill.getTargetType(), activeChar, skill, activeChar.getTarget(L2Creature.class));
	}
}
