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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;

public class L2SkillAgathion extends L2Skill
{
	private final int _npcId;
	
	public L2SkillAgathion(StatsSet set)
	{
		super(set);
		_npcId = set.getInteger("npcId", 0);
	}
	
	@Override
	public void useSkill(L2Creature caster, L2Creature... targets)
	{
		if (caster.isAlikeDead() || !(caster instanceof L2Player))
			return;
		
		L2Player activeChar = (L2Player)caster;
		activeChar.setAgathionId(_npcId);
		activeChar.broadcastUserInfo();
	}
}
