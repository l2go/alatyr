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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.ISkillConditionChecker;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSweep;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author _drunk_
 */
public class Sweep extends ISkillConditionChecker
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.SWEEP };
	
	@Override
	public boolean checkConditions(L2Creature activeChar, L2Skill skill, L2Creature target)
	{
		// Check if the skill is Sweep type and if conditions not apply
		if (target instanceof L2Attackable)
		{
			int spoilerId = ((L2Attackable)target).getIsSpoiledBy();
			
			if (((L2Attackable)target).isDead())
			{
				if (!((L2Attackable)target).isSpoil())
				{
					// Send a System Message to the L2Player
					activeChar.sendPacket(SystemMessageId.SWEEPER_FAILED_TARGET_NOT_SPOILED);
					return false;
				}
				
				if (activeChar.getObjectId() != spoilerId && !((L2Player)activeChar).isInLooterParty(spoilerId))
				{
					// Send a System Message to the L2Player
					activeChar.sendPacket(SystemMessageId.SWEEP_NOT_ALLOWED);
					return false;
				}
			}
		}
		
		return super.checkConditions(activeChar, skill, target);
	}
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill tmpSkill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
		{
			return;
		}
		
		L2SkillSweep skill = (L2SkillSweep)tmpSkill;
		
		L2Player player = (L2Player)activeChar;
		InventoryUpdate iu = Config.FORCE_INVENTORY_UPDATE ? null : new InventoryUpdate();
		boolean send = false;
		
		for (L2Creature element : targets)
		{
			if (!(element instanceof L2Attackable))
				continue;
			
			L2Attackable target = (L2Attackable)element;
			
			L2Attackable.RewardItem[] items = null;
			boolean isSweeping = false;
			synchronized (target)
			{
				if (target.isSweepActive())
				{
					items = target.takeSweep();
					isSweeping = true;
				}
			}
			if (isSweeping)
			{
				if (skill.getAbsorbAbs() > 0)
				{
					double hpAdd = skill.getAbsorbAbs();
					double hp = Math.min(activeChar.getStatus().getCurrentHp() + hpAdd, activeChar.getMaxHp());
					double hpDiff = hp - activeChar.getStatus().getCurrentHp();
					
					activeChar.getStatus().increaseHp(hpDiff);
				}
				if (items == null || items.length == 0)
					continue;
				for (L2Attackable.RewardItem ritem : items)
				{
					if (player.isInParty())
						player.getParty().distributeItem(player, ritem, true, target);
					else
					{
						L2ItemInstance item =
								player.getInventory().addItem("Sweep", ritem.getItemId(), ritem.getCount(), player,
										target);
						if (iu != null)
							iu.addItem(item);
						send = true;
						
						SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2); // you picked up $s1$s2
						smsg.addNumber(ritem.getCount());
						smsg.addItemName(item);
						player.sendPacket(smsg);
					}
				}
			}
			target.endDecayTask();
			if (send)
			{
				if (iu != null)
					player.sendPacket(iu);
				else
					player.sendPacket(new ItemList(player, false));
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
