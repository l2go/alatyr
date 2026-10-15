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
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

/**
 * @author l3x
 */
public class Harvest implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.HARVEST };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill, L2Creature... targets)
	{
		if (!(activeChar instanceof L2Player))
			return;
		
		L2Player activePlayer = (L2Player)activeChar;
		
		InventoryUpdate iu = Config.FORCE_INVENTORY_UPDATE ? null : new InventoryUpdate();
		
		if (_log.isDebugEnabled())
			_log.info("Casting harvest");
		
		for (L2Creature element : targets)
		{
			if (!(element instanceof L2MonsterInstance))
				continue;
			
			L2MonsterInstance target = (L2MonsterInstance)element;
			
			if (activePlayer != target.getSeeder())
			{
				activePlayer.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_HARVEST);
				continue;
			}
			
			boolean send = false;
			int total = 0;
			int cropId = 0;
			
			if (target.isSeeded())
			{
				if (calcSuccess(activePlayer, target))
				{
					L2Attackable.RewardItem[] items = target.takeHarvest();
					if (items != null && items.length > 0)
					{
						for (L2Attackable.RewardItem ritem : items)
						{
							cropId = ritem.getItemId(); // always got 1 type of
							// crop as reward
							if (activePlayer.isInParty())
								activePlayer.getParty().distributeItem(activePlayer, ritem, true, target);
							else
							{
								L2ItemInstance item =
										activePlayer.getInventory().addItem("Manor", ritem.getItemId(),
												ritem.getCount(), activePlayer, target);
								if (iu != null)
									iu.addItem(item);
								send = true;
								total += ritem.getCount();
							}
						}
						if (send)
						{
							SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
							smsg.addNumber(total);
							smsg.addItemName(cropId);
							activePlayer.sendPacket(smsg);
							if (activePlayer.getParty() != null)
							{
								smsg = new SystemMessage(SystemMessageId.C1_HARVESTED_S3_S2S);
								smsg.addString(activePlayer.getName());
								smsg.addNumber(total);
								smsg.addItemName(cropId);
								activePlayer.getParty().broadcastToPartyMembers(activePlayer, smsg);
							}
							
							if (iu != null)
								activePlayer.sendPacket(iu);
							else
								activePlayer.sendPacket(new ItemList(activePlayer, false));
						}
					}
				}
				else
				{
					activePlayer.sendPacket(SystemMessageId.THE_HARVEST_HAS_FAILED);
				}
			}
			else
			{
				activePlayer.sendPacket(SystemMessageId.THE_HARVEST_FAILED_BECAUSE_THE_SEED_WAS_NOT_SOWN);
			}
		}
	}
	
	private boolean calcSuccess(L2Player activePlayer, L2MonsterInstance target)
	{
		int basicSuccess = 100;
		int levelPlayer = activePlayer.getLevel();
		int levelTarget = target.getLevel();
		
		int diff = (levelPlayer - levelTarget);
		if (diff < 0)
			diff = -diff;
		
		// Apply penalty, target <=> player levels
		// 5% penalty for each level
		if (diff > 5)
		{
			basicSuccess -= (diff - 5) * 5;
		}
		
		// success rate cant be less than 1%
		if (basicSuccess < 1)
			basicSuccess = 1;
		
		int rate = Rnd.nextInt(99);
		
		return rate < basicSuccess;
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
