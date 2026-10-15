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
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author  Kerberos
 */
public class PetFood implements IItemHandler
{
	private static final int[] ITEM_IDS = { 2515, 4038, 5168, 5169, 6316, 7582, 9668, 10425 };
	
	/**
	 * @see com.l2jfree.gameserver.handler.IItemHandler#useItem(com.l2jfree.gameserver.gameobjects.instance.L2Playable, com.l2jfree.gameserver.model.items.L2ItemInstance)
	 */
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		int itemId = item.getItemId();
		switch (itemId)
		{
			case 2515: // Wolf's food
				useFood(playable, 2048, item);
				break;
			case 4038: // Hatchling's food
				useFood(playable, 2063, item);
				break;
			case 5168: // Strider's food
				useFood(playable, 2101, item);
				break;
			case 5169: // ClanHall / Castle Strider's food
				useFood(playable, 2102, item);
				break;
			case 6316: // Wyvern's food
				useFood(playable, 2180, item);
				break;
			case 7582: // Baby Pet's food
				useFood(playable, 2048, item);
				break;
			case 9668: // Great Wolf's food
				useFood(playable, 2361, item);
				break;
			case 10425: // Improved Baby Pet's food
				useFood(playable, 2361, item);
				break;
		}
	}
	
	public boolean useFood(L2Playable activeChar, int magicId, L2ItemInstance item)
	{
		L2Skill skill = SkillTable.getInstance().getInfo(magicId, 1);
		if (skill != null)
		{
			if (activeChar instanceof L2PetInstance)
			{
				L2PetInstance pet = (L2PetInstance)activeChar;
				if (pet.destroyItem("Consume", item.getObjectId(), 1, null, false))
				{
					pet.broadcastPacket(new MagicSkillUse(pet, pet, magicId, 1, 0, 0));
					pet.setCurrentFed((int)(pet.getCurrentFed() + (skill.getFeed() * Config.PET_FOOD_RATE)));
					pet.broadcastStatusUpdate();
					if (pet.getCurrentFed() < (0.55 * pet.getPetData().getPetMaxFeed()))
						pet.getOwner().sendPacket(SystemMessageId.YOUR_PET_ATE_A_LITTLE_BUT_IS_STILL_HUNGRY);
					return true;
				}
			}
			else if (activeChar instanceof L2Player)
			{
				L2Player player = (L2Player)activeChar;
				int itemId = item.getItemId();
				if (player.isMounted())
				{
					int petId = player.getMountNpcId();
					
					if (PetDataTable.isPetFood(petId, itemId))
					{
						if (player.destroyItem("Consume", item.getObjectId(), 1, null, false))
						{
							player.broadcastPacket(new MagicSkillUse(player, player, magicId, 1, 0, 0));
							player.setCurrentFeed(player.getCurrentFeed() + skill.getFeed());
						}
						return true;
					}
					else
					{
						player.sendPacket(new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addItemName(item));
						return false;
					}
				}
				else
					player.sendPacket(new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addItemName(item));
				return false;
			}
		}
		return false;
	}
	
	/**
	 * @see com.l2jfree.gameserver.handler.IItemHandler#getItemIds()
	 */
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
