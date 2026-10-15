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
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 *
 * @author evill33t
 */
public class MakeKillable implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.MAKE_KILLABLE };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		for (L2Creature element : targets)
		{
			if (!(element instanceof L2MonsterInstance))
				continue;
			
			L2MonsterInstance target = (L2MonsterInstance)element;
			
			if (!target.isKillable())
				target.setKillable(true);
			else
			{
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
