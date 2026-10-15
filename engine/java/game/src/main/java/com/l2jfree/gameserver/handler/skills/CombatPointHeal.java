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
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class CombatPointHeal implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.COMBATPOINTHEAL, L2SkillType.CPHEAL_PERCENT };
	
	@Override
	public void useSkill(L2Creature actChar, L2Skill skill, L2Creature... targets)
	{
		SkillHandler.getInstance().useSkill(L2SkillType.BUFF, actChar, skill, targets);
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			double cp = skill.getPower();
			
			if (skill.getSkillType() == L2SkillType.CPHEAL_PERCENT)
				cp = target.getMaxCp() * cp / 100;
			
			// From CT2 u will receive exact CP, you can't go over it, if you have full CP and you get CP buff, you will receive 0CP restored message
			cp = Math.min(cp, target.getMaxCp() - target.getStatus().getCurrentCp());
			
			if (target instanceof L2Player)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_CP_WILL_BE_RESTORED);
				sm.addNumber((int)cp);
				((L2Player)target).sendPacket(sm);
			}
			
			target.getStatus().setCurrentCp(cp + target.getStatus().getCurrentCp());
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
