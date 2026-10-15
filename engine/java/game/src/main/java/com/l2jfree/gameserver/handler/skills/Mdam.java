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
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.handler.ICubicSkillHandler;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

public final class Mdam implements ICubicSkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.MDAM, L2SkillType.DEATHLINK };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (activeChar.isAlikeDead())
			return;
		
		boolean ss = false;
		boolean bss = false;
		
		if (activeChar.isBlessedSpiritshotCharged())
		{
			bss = true;
			activeChar.useBlessedSpiritshotCharge();
		}
		else if (activeChar.isSpiritshotCharged())
		{
			ss = true;
			activeChar.useSpiritshotCharge();
		}
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			// TODO: should be moved to skill target list generation
			if (GlobalRestrictions.isProtected(activeChar, target, skill, true))
				continue;
			
			if (target.isAlikeDead())
			{
				if (activeChar instanceof L2Player && target instanceof L2Player && target.isFakeDeath())
					target.stopFakeDeath(true);
				else
					continue;
			}
			
			final byte shld = Formulas.calcShldUse(activeChar, target, skill);
			final boolean mcrit = Formulas.calcMCrit(activeChar.getMCriticalHit(target, skill));
			final double damage = Formulas.calcMagicDam(activeChar, target, skill, shld, ss, bss, mcrit);
			final byte reflect = Formulas.calcSkillReflect(target, skill);
			
			skill.dealDamage(activeChar, target, skill, damage, reflect, mcrit, false);
			// Actually, the effect failure message is sent before dmg
			skill.getEffects(activeChar, target, reflect, shld, false, ss, bss);
		}
		
		// Activate attacked effects, if any
		if (skill.getId() == 4139 && activeChar instanceof L2Summon) //big boom unsummon-destroy
		{
			((L2Summon)activeChar).unSummon();
		}
	}
	
	@Override
	public void useCubicSkill(L2CubicInstance activeCubic, L2Skill skill, L2Creature... targets)
	{
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isAlikeDead())
			{
				if (target instanceof L2Player && target.isFakeDeath())
					target.stopFakeDeath(true);
				else
					continue;
			}
			
			boolean mcrit = Formulas.calcMCrit(activeCubic.getMCriticalHit(target, skill));
			byte shld = Formulas.calcShldUse(activeCubic.getOwner(), target, skill);
			int damage = (int)Formulas.calcMagicDam(activeCubic, target, skill, mcrit, shld);
			
			/*
			 *  If target is reflecting the skill then no damage is done
			 *  Ignoring vengance-like reflections
			 */
			if ((Formulas.calcSkillReflect(target, skill) & Formulas.SKILL_REFLECT_SUCCEED) > 0)
				damage = 0;
			
			if (_log.isDebugEnabled())
				_log.info("L2SkillMdam: useCubicSkill() -> damage = " + damage);
			
			if (damage > 0)
			{
				// Manage attack or cast break of the target (calculating rate, sending message...)
				if (!target.isRaid() && Formulas.calcAtkBreak(target, damage))
				{
					target.breakAttack();
					target.breakCast();
				}
				
				activeCubic.getOwner().sendDamageMessage(target, damage, mcrit, false, false);
				
				if (skill.hasEffects())
				{
					// activate attacked effects, if any
					if (Formulas.calcCubicSkillSuccess(activeCubic, target, skill, shld))
					{
						skill.getEffects(activeCubic, target);
					}
				}
				
				target.reduceCurrentHp(damage, activeCubic.getOwner(), skill);
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
