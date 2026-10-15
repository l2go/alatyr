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
import com.l2jfree.gameserver.model.L2Fishing;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

public class FishingSkill implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.PUMPING, L2SkillType.REELING };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player player = (L2Player)activeChar;
		
		L2Fishing fish = player.getFishCombat();
		if (fish == null)
		{
			if (skill.getSkillType() == L2SkillType.PUMPING)
			{
				//Pumping skill is available only while fishing
				player.sendPacket(SystemMessageId.CAN_USE_PUMPING_ONLY_WHILE_FISHING);
			}
			else if (skill.getSkillType() == L2SkillType.REELING)
			{
				//Reeling skill is available only while fishing
				player.sendPacket(SystemMessageId.CAN_USE_REELING_ONLY_WHILE_FISHING);
			}
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		L2Weapon weaponItem = player.getActiveWeaponItem();
		L2ItemInstance weaponInst = activeChar.getActiveWeaponInstance();
		if (weaponInst == null || weaponItem == null)
			return;
		int SS = 1;
		int pen = 0;
		if (weaponInst.isFishshotCharged())
			SS = 2;
		double gradebonus = 1 + weaponItem.getCrystalGrade() * 0.1;
		int dmg = (int)(skill.getPower() * gradebonus * SS);
		if (player.getSkillLevel(1315) <= skill.getLevel() - 2) //1315 - Fish Expertise
		{ //Penalty
			player.sendPacket(SystemMessageId.REELING_PUMPING_3_LEVELS_HIGHER_THAN_FISHING_PENALTY);
			pen = 50;
			int penatlydmg = dmg - pen; //
			if (player.isGM())
				player.sendMessage("Dmg w/o penalty = " + dmg);
			dmg = penatlydmg;
		}
		if (SS > 1)
		{
			weaponInst.useFishshotCharge();
		}
		if (skill.getSkillType() == L2SkillType.REELING)//Realing
		{
			fish.useRealing(dmg, pen);
		}
		else
		// Pumping
		{
			fish.usePomping(dmg, pen);
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
