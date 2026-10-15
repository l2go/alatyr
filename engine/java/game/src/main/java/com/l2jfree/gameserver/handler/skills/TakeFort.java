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
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.util.Util;

/**
 * @author _drunk_
 */
public class TakeFort implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.TAKEFORT };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player player = (L2Player)activeChar;
		
		L2Object target = player.getTarget();
		
		if (player.getClan() == null)
			return;
		
		if (target == null)
			return;
		
		Fort fort = FortManager.getInstance().getFort(player);
		if (fort == null || !checkIfOkToCastFlagDisplay(player, fort, true, skill, target))
			return;
		
		fort.endOfSiege(player.getClan());
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
	
	/**
	 * Return true if character clan place a flag<BR><BR>
	 *
	 * @param activeChar The L2Creature of the character placing the flag
	 *
	 */
	public static boolean checkIfOkToCastFlagDisplay(L2Creature activeChar, boolean isCheckOnly, L2Skill skill,
			L2Object target)
	{
		return checkIfOkToCastFlagDisplay(activeChar, FortManager.getInstance().getFort(activeChar), isCheckOnly,
				skill, target);
	}
	
	public static boolean checkIfOkToCastFlagDisplay(L2Creature activeChar, Fort fort, boolean isCheckOnly,
			L2Skill skill, L2Object target)
	{
		if (activeChar == null || !(activeChar instanceof L2Player))
			return false;
		
		SystemMessage sm;
		L2Player player = (L2Player)activeChar;
		
		if (fort == null || fort.getFortId() <= 0)
		{
			sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		}
		else if (!fort.getSiege().getIsInProgress())
		{
			sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		}
		else if (!Util.checkIfInRange(200, player, player.getTarget(), true))
		{
			sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		}
		else if (fort.getSiege().getAttackerClan(player.getClan()) == null)
		{
			sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
		}
		else
		{
			if (!isCheckOnly)
				fort.getSiege().announceToPlayer(new SystemMessage(SystemMessageId.S1_TRYING_RAISE_FLAG),
						player.getClan().getName());
			return true;
		}
		
		if (!isCheckOnly)
		{
			player.sendPacket(sm);
		}
		return false;
	}
}
