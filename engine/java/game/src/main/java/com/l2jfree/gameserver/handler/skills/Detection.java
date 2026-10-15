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
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;

/**
 * @author ZaKax
 */
public class Detection implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.DETECTION };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		final boolean hasParty;
		final boolean hasClan;
		final boolean hasAlly;
		final L2Player player = activeChar.getActingPlayer();
		if (player != null)
		{
			hasParty = player.isInParty();
			hasClan = player.getClanId() > 0;
			hasAlly = player.getAllyId() > 0;
		}
		else
		{
			hasParty = false;
			hasClan = false;
			hasAlly = false;
		}
		
		for (L2Player target : activeChar.getKnownList().getKnownPlayersInRadius(skill.getSkillRadius()))
		{
			if (target != null && target.getAppearance().isInvisible())
			{
				if (player != null)
				{
					if (hasParty && target.getParty() != null && player.getParty() == target.getParty())
						continue;
					if (hasClan && player.getClanId() == target.getClanId())
						continue;
					if (hasAlly && player.getAllyId() == target.getAllyId())
						continue;
				}
				
				L2Effect eHide = target.getFirstEffect(L2EffectType.HIDE);
				if (eHide != null)
					eHide.exit();
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
