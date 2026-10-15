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


import java.util.ArrayList;
import java.util.List;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.2.5.2.4 $ $Date: 2005/04/03 15:55:03 $
 */

public class Resurrect implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.RESURRECT };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		L2Player player = null;
		if (activeChar instanceof L2Player)
			player = (L2Player)activeChar;
		
		L2Player targetPlayer;
		List<L2Creature> targetToRes = new ArrayList<L2Creature>();
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			if (target.isEradicated())
				continue;
			
			if (target instanceof L2Player)
			{
				targetPlayer = (L2Player)target;
				
				// Check for same party or for same clan, if target is for clan.
				if (skill.getTargetType() == SkillTargetType.TARGET_CORPSE_CLAN)
				{
					if (player != null && player.getClanId() != targetPlayer.getClanId())
						continue;
				}
			}
			
			if (target.isVisible())
				targetToRes.add(target);
		}
		
		for (L2Creature cha : targetToRes)
		{
			if (activeChar instanceof L2Player)
			{
				if (cha instanceof L2Player)
				{
					((L2Player)cha).reviveRequest((L2Player)activeChar, skill);
				}
				else if (cha instanceof L2PetInstance)
				{
					if (((L2PetInstance)cha).getOwner() == activeChar)
						cha.doRevive(Formulas.calculateSkillResurrectRestorePercent(skill, activeChar));
					else
						((L2PetInstance)cha).getOwner().revivePetRequest((L2Player)activeChar, skill);
				}
				else
					cha.doRevive(Formulas.calculateSkillResurrectRestorePercent(skill, activeChar));
			}
			else
			{
				DecayTaskManager.getInstance().cancelDecayTask(cha);
				cha.doRevive(Formulas.calculateSkillResurrectRestorePercent(skill, activeChar));
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
