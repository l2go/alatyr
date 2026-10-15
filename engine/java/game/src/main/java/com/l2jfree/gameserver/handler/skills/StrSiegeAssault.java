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
import com.l2jfree.gameserver.handler.ISkillConditionChecker;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

/**
 * @author _tomciaaa_
 */
public final class StrSiegeAssault extends ISkillConditionChecker
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.STRSIEGEASSAULT };
	
	@Override
	public boolean checkConditions(L2Creature activeChar, L2Skill skill)
	{
		if (!(activeChar instanceof L2Player))
			return false;
		
		final L2Player player = (L2Player)activeChar;
		
		if (!SiegeManager.checkIfOkToUseStriderSiegeAssault(player, false)
				&& !FortSiegeManager.checkIfOkToUseStriderSiegeAssault(player, false))
			return false;
		
		return super.checkConditions(activeChar, skill);
	}
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player player = (L2Player)activeChar;
		
		if (SiegeManager.checkIfOkToUseStriderSiegeAssault(player, false)
				|| FortSiegeManager.checkIfOkToUseStriderSiegeAssault(player, false)
				|| CCHManager.checkIfOkToUseStriderSiegeAssault(player, false))
		{
			SkillHandler.getInstance().useSkill(L2SkillType.PDAM, activeChar, skill, targets);
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
