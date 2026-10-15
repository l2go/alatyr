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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.handler.ISkillConditionChecker;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.FlyToLocation;
import com.l2jfree.gameserver.network.packets.server.FlyToLocation.FlyType;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.gameserver.util.Util;

/**
 * Some parts taken from EffectWarp, which cannot be used for this case.
 * @author Didldak
 */
public class InstantJump extends ISkillConditionChecker
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.INSTANT_JUMP };
	
	@Override
	public boolean checkConditions(L2Creature activeChar, L2Skill skill)
	{
		// You cannot jump while rooted right ;)
		if (activeChar.isRooted())
		{
			if (activeChar instanceof L2Player)
			{
				// Sends message that skill cannot be used...
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
				sm.addSkillName(skill);
				activeChar.getActingPlayer().sendPacket(sm);
			}
			return false;
		}
		
		// And this skill cannot be used in peace zone, not even on NPCs!
		if (activeChar.isInsideZone(L2Zone.FLAG_PEACE))
		{
			activeChar.sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
			return false;
		}
		
		return super.checkConditions(activeChar, skill);
	}
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (targets.length == 0 || targets[0] == null)
			return;
		
		L2Creature target = targets[0];
		
		int x = 0, y = 0, z = 0;
		
		int px = target.getX();
		int py = target.getY();
		double ph = Util.convertHeadingToDegree(target.getHeading());
		
		ph += 180;
		
		if (ph > 360)
			ph -= 360;
		
		ph = (Math.PI * ph) / 180;
		
		x = (int)(px + (25 * Math.cos(ph)));
		y = (int)(py + (25 * Math.sin(ph)));
		z = target.getZ();
		
		activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		activeChar.broadcastPacket(new FlyToLocation(activeChar, x, y, z, FlyType.DUMMY));
		activeChar.abortAttack();
		activeChar.abortCast();
		
		activeChar.getPosition().setXYZ(x, y, z);
		activeChar.broadcastPacket(new ValidateLocation(activeChar));
		
		if (skill.hasEffects())
		{
			if (Formulas.calcSkillReflect(target, skill) == Formulas.SKILL_REFLECT_SUCCEED)
			{
				skill.getEffects(target, activeChar);
				//SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
				//sm.addSkillName(skill);
				//activeChar.sendPacket(sm);
			}
			else
			{
				// activate attacked effects, if any
				byte shld = Formulas.calcShldUse(activeChar, target, skill);
				if (Formulas.calcSkillSuccess(activeChar, target, skill, shld, false, false, false))
				{
					skill.getEffects(activeChar, target);
					
					//SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
					//sm.addSkillName(skill);
					//target.sendPacket(sm);
				}
				else
					activeChar.sendResistedMyEffectMessage(target, skill);
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
