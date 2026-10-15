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
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.2.2.2.1 $ $Date: 2005/03/02 15:38:36 $
 */

public class ManaHeal implements ISkillHandler
{
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IItemHandler#useItem(com.l2jfree.gameserver.model.L2Player, com.l2jfree.gameserver.model.L2ItemInstance)
	 */
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.MANAHEAL, L2SkillType.MANARECHARGE,
			L2SkillType.MANAHEAL_PERCENT };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IItemHandler#useItem(com.l2jfree.gameserver.model.L2Player, com.l2jfree.gameserver.model.L2ItemInstance)
	 */
	@Override
	public void useSkill(L2Creature actChar, L2Skill skill, L2Creature... targets)
	{
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			double mp = skill.getPower();
			if (skill.getSkillType() == L2SkillType.MANAHEAL_PERCENT)
			{
				//double mp = skill.getPower();
				mp = target.getMaxMp() * mp / 100.0;
			}
			else
			{
				mp =
						(skill.getSkillType() == L2SkillType.MANARECHARGE) ? target.calcStat(Stats.RECHARGE_MP_RATE,
								mp, null, null) : mp;
			}
			
			final int levelDiff = target.getLevel() - actChar.getLevel();
			
			if (3 < levelDiff)
			{
				switch (levelDiff)
				{
					case 4:
					case 5:
						mp *= 0.6;
						break;
					case 6:
					case 7:
						mp *= 0.4;
						break;
					case 8:
					case 9:
						mp *= 0.3;
						break;
					default:
						mp *= 0.1;
						break;
				}
			}
			
			// From CT2 you will receive exact MP, you can't go over it, if you have full MP and you get MP buff, you will receive 0MP restored message
			if ((target.getStatus().getCurrentMp() + mp) >= target.getMaxMp())
			{
				mp = target.getMaxMp() - target.getStatus().getCurrentMp();
			}
			
			target.getStatus().setCurrentMp(mp + target.getStatus().getCurrentMp());
			
			if (target instanceof L2Player)
			{
				if (actChar instanceof L2Player && actChar != target)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.S2_MP_RESTORED_BY_C1);
					sm.addString(actChar.getName());
					sm.addNumber((int)mp);
					target.getActingPlayer().sendPacket(sm);
				}
				else
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_MP_RESTORED);
					sm.addNumber((int)mp);
					target.getActingPlayer().sendPacket(sm);
				}
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
