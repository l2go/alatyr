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
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

public class CpDam implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.CPDAM, L2SkillType.CPDAMPERCENT };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		/*
		boolean ss = false;
		boolean sps = false;
		boolean bss = false;
		
		if (skill.useSpiritShot())
		{
			if (activeChar.isBlessedSpiritshotCharged())
			{
				bss = true;
				activeChar.useBlessedSpiritshotCharge();
			}
			else if (activeChar.isSpiritshotCharged())
			{
				sps = true;
				activeChar.useSpiritshotCharge();
			}
		}
		else if (skill.useSoulShot())
		{
			if (activeChar.isSoulshotCharged())
			{
				ss = true;
				activeChar.useSoulshotCharge();
			}
		}
		*/
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isAlikeDead())
			{
				if (activeChar instanceof L2Player && target instanceof L2Player && target.isFakeDeath())
					target.stopFakeDeath(true);
				else
					continue;
			}
			
			// With the current skills this makes no sense for me...
			/*
			byte shld = Formulas.calcShldUse(activeChar, target, skill);
			if (!Formulas.calcSkillSuccess(activeChar, target, skill, shld, ss, sps, bss))
				return;
			*/
			
			final int damage;
			
			if (skill.getSkillType() == L2SkillType.CPDAMPERCENT)
				damage = (int)(target.getCurrentCp() * skill.getPower() / 100);
			else
				damage = (int)skill.getPower();
			
			// Manage attack or cast break of the target (calculating rate, sending message...)
			if (Formulas.calcAtkBreak(target, damage))
			{
				target.breakAttack();
				target.breakCast();
			}
			
			skill.getEffects(activeChar, target);
			
			activeChar.sendDamageMessage(target, damage, false, false, false);
			target.getStatus().setCurrentCp(target.getStatus().getCurrentCp() - damage);
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
