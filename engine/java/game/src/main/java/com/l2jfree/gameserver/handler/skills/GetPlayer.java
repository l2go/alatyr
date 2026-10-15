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
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.tools.random.Rnd;

/*
 * Mobs can teleport players to them
 */

public class GetPlayer implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.GET_PLAYER };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (activeChar.isAlikeDead())
			return;
		
		for (L2Object target : targets)
		{
			if (target instanceof L2Player)
			{
				L2Player trg = (L2Player)target;
				
				if (trg.isAlikeDead())
					continue;
				// GetPlayer is a handler for skills of mobs, eg "Zaken" or "Porta"
				trg.getPosition().setXYZ(activeChar.getX() + Rnd.get(-10, 10), activeChar.getY() + Rnd.get(-10, 10),
						activeChar.getZ());
				trg.stopMove(null);
				trg.sendPacket(new ValidateLocation(trg));
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
