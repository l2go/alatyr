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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.templates.StatsSet;

public class L2SkillDrain extends L2Skill
{
	private final float _absorbPart;
	private final int _absorbAbs;
	
	public L2SkillDrain(StatsSet set)
	{
		super(set);
		
		_absorbPart = set.getFloat("absorbPart", 0.f);
		_absorbAbs = set.getInteger("absorbAbs", 0);
	}
	
	@Override
	public void useSkill(L2Creature activeChar, L2Creature... targets)
	{
		if (activeChar.isAlikeDead())
			return;
		
		boolean ss = false;
		boolean bss = false;
		
		for (L2Creature target : targets)
		{
			if (target.isAlikeDead() && getTargetType() != SkillTargetType.TARGET_CORPSE_MOB)
				continue;
			
			if (activeChar != target && target.isInvul())
				continue; // No effect on invulnerable chars unless they cast it themselves.
				
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
			
			boolean mcrit = Formulas.calcMCrit(activeChar.getMCriticalHit(target, this));
			byte shld = Formulas.calcShldUse(activeChar, target, this);
			int damage = (int)Formulas.calcMagicDam(activeChar, target, this, shld, ss, bss, mcrit);
			
			int _drain = 0;
			int _cp = (int)target.getStatus().getCurrentCp();
			int _hp = (int)target.getStatus().getCurrentHp();
			
			if (_cp > 0)
			{
				if (damage < _cp)
					_drain = 0;
				else
					_drain = damage - _cp;
			}
			else if (damage > _hp)
				_drain = _hp;
			else
				_drain = damage;
			
			double hpAdd = _absorbAbs + _absorbPart * _drain;
			double hp = Math.min(activeChar.getStatus().getCurrentHp() + hpAdd, activeChar.getMaxHp());
			
			double hpDiff = hp - activeChar.getStatus().getCurrentHp();
			
			activeChar.getStatus().increaseHp(hpDiff);
			
			// Check to see if we should damage the target
			if (damage > 0 && (!target.isDead() || getTargetType() != SkillTargetType.TARGET_CORPSE_MOB))
			{
				if (activeChar instanceof L2Player)
				{
					L2Player activeCaster = (L2Player)activeChar;
					
					if (activeCaster.isGM() && activeCaster.getAccessLevel() < Config.GM_CAN_GIVE_DAMAGE)
						damage = 0;
				}
				
				// Manage attack or cast break of the target (calculating rate, sending message...)
				if (Formulas.calcAtkBreak(target, damage))
				{
					target.breakAttack();
					target.breakCast();
				}
				
				activeChar.sendDamageMessage(target, damage, mcrit, false, false);
				
				if (hasEffects() && getTargetType() != SkillTargetType.TARGET_CORPSE_MOB)
				{
					if ((Formulas.calcSkillReflect(target, this) & Formulas.SKILL_REFLECT_SUCCEED) > 0)
					{
						getEffects(target, activeChar);
						if (activeChar instanceof L2Player)
							activeChar.getActingPlayer().sendPacket(
									new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(this));
					}
					else
					{
						// activate attacked effects, if any
						if (Formulas.calcSkillSuccess(activeChar, target, this, shld, false, ss, bss))
							getEffects(activeChar, target);
						else if (activeChar.getActingPlayer() != null)
							activeChar.sendResistedMyEffectMessage(target, this);
					}
				}
				
				target.reduceCurrentHp(damage, activeChar, this);
			}
			// Check to see if we should do the decay right after the cast
			if (target.isDead() && getTargetType() == SkillTargetType.TARGET_CORPSE_MOB && target instanceof L2Npc)
				((L2Npc)target).endDecayTask();
		}
	}
	
	public void useCubicSkill(L2CubicInstance activeCubic, L2Creature... targets)
	{
		if (_log.isDebugEnabled())
			_log.info("L2SkillDrain: useCubicSkill()");
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isAlikeDead() && getTargetType() != SkillTargetType.TARGET_CORPSE_MOB)
				continue;
			
			boolean mcrit = Formulas.calcMCrit(activeCubic.getMCriticalHit(target, this));
			byte shld = Formulas.calcShldUse(activeCubic.getOwner(), target, this);
			int damage = (int)Formulas.calcMagicDam(activeCubic, target, this, mcrit, shld);
			if (_log.isDebugEnabled())
				_log.info("L2SkillDrain: useCubicSkill() -> damage = " + damage);
			
			double hpAdd = _absorbAbs + _absorbPart * damage;
			L2Player owner = activeCubic.getOwner();
			double hp = Math.min(owner.getStatus().getCurrentHp() + hpAdd, owner.getMaxHp());
			
			owner.getStatus().setCurrentHp(hp);
			
			// Check to see if we should damage the target
			if (damage > 0 && (!target.isDead() || getTargetType() != SkillTargetType.TARGET_CORPSE_MOB))
			{
				target.reduceCurrentHp(damage, activeCubic.getOwner(), this);
				
				// Manage attack or cast break of the target (calculating rate, sending message...)
				if (!target.isRaid() && Formulas.calcAtkBreak(target, damage))
				{
					target.breakAttack();
					target.breakCast();
				}
				owner.sendDamageMessage(target, damage, mcrit, false, false);
			}
		}
	}
}
