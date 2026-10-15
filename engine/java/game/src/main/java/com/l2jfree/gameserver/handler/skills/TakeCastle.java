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
import com.l2jfree.gameserver.gameobjects.instance.L2ArtefactInstance;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

/**
 * @author _drunk_
 */
public class TakeCastle implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.TAKECASTLE };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player player = (L2Player)activeChar;
		
		if (player.getClan() == null || player.getClan().getLeaderId() != player.getObjectId())
			return;
		
		Castle castle = CastleManager.getInstance().getCastle(player);
		if (castle == null || !SiegeManager.getInstance().checkIfOkToCastSealOfRule(player, castle))
			return;
		
		if (targets.length > 0 && targets[0] instanceof L2ArtefactInstance)
			castle.engrave(player.getClan(), targets[0].getObjectId());
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
	public static boolean checkIfOkToCastSealOfRule(L2Creature activeChar)
	{
		return SiegeManager.getInstance().checkIfOkToCastSealOfRule(activeChar,
				CastleManager.getInstance().getCastle(activeChar));
	}
}
