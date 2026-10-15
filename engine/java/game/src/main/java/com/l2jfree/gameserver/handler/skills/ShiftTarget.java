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
package com.l2jfree.gameserver.handler.skills;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

/**
 * @author Sephiroth
 */
public class ShiftTarget implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.SHIFT_TARGET };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		L2Attackable attackerChar = null;
		L2Npc attacker = null;
		L2Player targetChar = null;
		
		boolean targetShifted = false;
		
		for (L2Object target : targets)
		{
			if (target instanceof L2Player)
			{
				targetChar = (L2Player)target;
				break;
			}
		}
		
		for (L2Object nearby : activeChar.getKnownList().getKnownCharactersInRadius(skill.getSkillRadius()))
		{
			if (!targetShifted)
			{
				if (nearby instanceof L2Attackable)
				{
					attackerChar = (L2Attackable)nearby;
					targetShifted = true;
					break;
				}
			}
		}
		
		if (targetShifted && attackerChar != null && targetChar != null)
		{
			attacker = attackerChar;
			int aggro = attackerChar.getHating(activeChar);
			
			if (aggro == 0)
			{
				if (targetChar.isRunning())
					attacker.setRunning();
				{
					attackerChar.addDamageHate(targetChar, 0, 1);
					attacker.setTarget(targetChar);
					attackerChar.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, targetChar);
				}
			}
			else
			{
				attackerChar.stopHating(activeChar);
				if (targetChar.isRunning())
					attacker.setRunning();
				{
					attackerChar.addDamageHate(targetChar, 0, aggro);
					attacker.setTarget(targetChar);
					attackerChar.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, targetChar);
				}
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
