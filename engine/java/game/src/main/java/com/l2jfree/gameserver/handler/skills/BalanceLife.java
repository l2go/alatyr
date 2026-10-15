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
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

/**
 * @author earendil
 */
public final class BalanceLife implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.BALANCE_LIFE };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		SkillHandler.getInstance().useSkill(L2SkillType.BUFF, activeChar, skill, targets);
		
		L2Player player = null;
		if (activeChar instanceof L2Player)
			player = (L2Player)activeChar;
		
		double fullHP = 0;
		double currentHPs = 0;
		
		for (L2Creature target : targets)
		{
			if (target == null || target.isDead())
				continue;
			else if (target != activeChar) // Player holding a cursed weapon can't be healed and can't heal
			{
				if (target instanceof L2Player && ((L2Player)target).isCursedWeaponEquipped())
					continue;
				else if (player != null && player.isCursedWeaponEquipped())
					continue;
			}
			
			fullHP += target.getMaxHp();
			currentHPs += target.getStatus().getCurrentHp();
		}
		
		final double percentHP = currentHPs / fullHP;
		final String message = "HP of the party has been balanced to " + ((int)(100 * percentHP)) + "%.";
		
		for (L2Creature target : targets)
		{
			if (target == null || target.isDead())
				continue;
			else if (target != activeChar) // Player holding a cursed weapon can't be healed and can't heal
			{
				if (target instanceof L2Player && ((L2Player)target).isCursedWeaponEquipped())
					continue;
				else if (player != null && player.isCursedWeaponEquipped())
					continue;
			}
			
			target.getStatus().increaseHp(target.getMaxHp() * percentHP - target.getStatus().getCurrentHp());
			
			if (target instanceof L2Player)
				((L2Player)target).sendMessage(message);
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
