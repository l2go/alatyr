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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExAttributeEnchantResult;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class RequestExEnchantItemAttribute extends L2ClientPacket
{
	private static final String D0_38_REQUEST_EX_ENCHANT_ITEM_ATTRIBUTE = "[C] D0 38 RequestExEnchantItemAttribute";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_objectId == 0xFFFF || _objectId == 0xFFFFFFFF) // which one should be here?
		{
			// Player canceled enchant
			player.setActiveEnchantAttrItem(null);
			requestFailed(SystemMessageId.ELEMENTAL_ENHANCE_CANCELED);
			return;
		}
		else if (player.isOnline() == 0)
		{
			player.setActiveEnchantAttrItem(null);
			return;
		}
		
		// Restrict enchant during restart/shutdown (because of an existing exploit)
		if (Shutdown.isActionDisabled(DisableType.ENCHANT))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		else if (player.getPrivateStoreType() != L2Player.STORE_PRIVATE_NONE)
		{
			requestFailed(SystemMessageId.CANNOT_ADD_ELEMENTAL_POWER_WHILE_OPERATING_PRIVATE_STORE_OR_WORKSHOP);
			return;
		}
		// Restrict enchant during a trade (bug if enchant fails)
		else if (player.isProcessingTransaction())
		{
			// Cancel trade
			player.cancelActiveTrade();
			requestFailed(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
			return;
		}
		
		L2ItemInstance item = player.getInventory().getItemByObjectId(_objectId);
		L2ItemInstance stone = player.getActiveEnchantAttrItem();
		if (item == null || stone == null || item.isWear())
		{
			player.setActiveEnchantAttrItem(null);
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			return;
		}
		else if ((item.getLocation() != L2ItemInstance.ItemLocation.INVENTORY)
				&& (item.getLocation() != L2ItemInstance.ItemLocation.PAPERDOLL))
		{
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			return;
		}
		
		int itemId = item.getItemId();
		
		//can't enchant rods, shadow items, adventurers', PvP items, hero items, cloaks, bracelets, underwear (e.g. shirt), belt, necklace, earring, ring
		if (item.getItem().getItemType() == L2WeaponType.ROD || item.isShadowItem() || item.isPvp()
				|| (!Config.ENCHANT_HERO_WEAPONS && item.isHeroItem()) || item.isTimeLimitedItem()
				|| (itemId >= 7816 && itemId <= 7831) || (item.getItem().getItemType() == L2WeaponType.NONE)
				|| item.getItem().getItemGradeSPlus() != L2Item.CRYSTAL_S
				|| item.getItem().getBodyPart() == L2Item.SLOT_BACK
				|| item.getItem().getBodyPart() == L2Item.SLOT_R_BRACELET
				|| item.getItem().getBodyPart() == L2Item.SLOT_UNDERWEAR
				|| item.getItem().getBodyPart() == L2Item.SLOT_BELT || item.getItem().getBodyPart() == L2Item.SLOT_NECK
				|| item.getItem().getBodyPart() == L2Item.SLOT_R_EAR
				|| item.getItem().getBodyPart() == L2Item.SLOT_R_FINGER)
		{
			player.sendPacket(SystemMessageId.ELEMENTAL_ENHANCE_REQUIREMENT_NOT_SUFFICIENT);
			player.setActiveEnchantAttrItem(null);
			return;
		}
		
		switch (item.getLocation())
		{
			case INVENTORY:
			case PAPERDOLL:
			{
				if (item.getOwnerId() != player.getObjectId())
				{
					requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
					player.setActiveEnchantAttrItem(null);
					return;
				}
				break;
			}
			default:
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				player.setActiveEnchantAttrItem(null);
				Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " tried to use enchant Exploit!",
						IllegalPlayerAction.PUNISH_KICK);
				return;
			}
		}
		
		int stoneId = stone.getItemId();
		Elementals oldElement = item.getElementals();
		int elementValue = oldElement == null ? 0 : oldElement.getValue();
		int limit = getLimit(stoneId, item);
		int powerToAdd = getPowerToAdd(stoneId, elementValue, item);
		byte elementToAdd = getElementFromItemId(stoneId);
		
		// Armors have the opposite element
		if (item.isArmor())
			elementToAdd = Elementals.getOppositeElement(elementToAdd);
		
		int newPower = elementValue + powerToAdd;
		if (newPower > limit)
		{
			newPower = limit;
			powerToAdd = limit - elementValue;
		}
		
		if (oldElement != null && oldElement.getElement() != elementToAdd && oldElement.getElement() != -2)
		{
			requestFailed(SystemMessageId.ANOTHER_ELEMENTAL_POWER_ALREADY_ADDED);
			player.setActiveEnchantAttrItem(null);
			return;
		}
		else if (powerToAdd <= 0)
		{
			requestFailed(SystemMessageId.ELEMENTAL_ENHANCE_CANCELED);
			player.setActiveEnchantAttrItem(null);
			return;
		}
		
		if (!player.destroyItem("AttrEnchant", stone, 1, player, true))
		{
			requestFailed(SystemMessageId.NOT_ENOUGH_ITEMS);
			player.setActiveEnchantAttrItem(null);
			return;
		}
		
		if (Rnd.get(100) < Config.ENCHANT_CHANCE_ELEMENT)
		{
			SystemMessage sm;
			if (item.getEnchantLevel() == 0)
			{
				sm = new SystemMessage(SystemMessageId.ELEMENTAL_POWER_S2_SUCCESSFULLY_ADDED_TO_S1);
				sm.addItemName(item);
				sm.addString(Elementals.getElementName(elementToAdd));
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.ELEMENTAL_POWER_S3_SUCCESSFULLY_ADDED_TO_S1_S2);
				sm.addNumber(item.getEnchantLevel());
				sm.addItemName(item);
				sm.addString(Elementals.getElementName(elementToAdd));
			}
			sendPacket(sm);
			item.setElementAttr(elementToAdd, newPower);
			
			if (item.isEquipped())
				item.updateElementAttrBonus(player);
			
			// send packets
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(item);
			sendPacket(iu);
		}
		else
			player.sendPacket(SystemMessageId.FAILED_ADDING_ELEMENTAL_POWER);
		
		sendPacket(new ExAttributeEnchantResult(powerToAdd));
		sendPacket(new UserInfo(player));
		
		sendAF();
		player.setActiveEnchantAttrItem(null);
	}
	
	public byte getElementFromItemId(int itemId)
	{
		byte element = 0;
		for (int id : Elementals.STONES)
		{
			if (id == itemId)
				return element;
			element++;
		}
		element = 0;
		for (int id : Elementals.ORES)
		{
			if (id == itemId)
				return element;
			element++;
		}
		element = 0;
		for (int id : Elementals.CRYSTALS)
		{
			if (id == itemId)
				return element;
			element++;
		}
		element = 0;
		for (int id : Elementals.JEWELS)
		{
			if (id == itemId)
				return element;
			element++;
		}
		element = 0;
		for (int id : Elementals.ENERGIES)
		{
			if (id == itemId)
				return element;
			element++;
		}
		return -1;
	}
	
	public int getLimit(int itemId, L2ItemInstance item)
	{
		for (int id : Elementals.STONES)
		{
			if (id == itemId)
			{
				if (item.isWeapon())
					return Elementals.WEAPON_VALUES[3];
				return Elementals.ARMOR_VALUES[3];
			}
		}
		for (int id : Elementals.ORES)
		{
			if (id == itemId)
			{
				if (item.isWeapon())
					return Elementals.WEAPON_VALUES[3];
				return Elementals.ARMOR_VALUES[3];
			}
		}
		for (int id : Elementals.CRYSTALS)
		{
			if (id == itemId)
			{
				if (item.isWeapon())
					return Elementals.WEAPON_VALUES[6];
				return Elementals.ARMOR_VALUES[6];
			}
		}
		for (int id : Elementals.JEWELS)
		{
			if (id == itemId)
			{
				if (item.isWeapon())
					return Elementals.WEAPON_VALUES[9];
				return Elementals.ARMOR_VALUES[9];
			}
		}
		for (int id : Elementals.ENERGIES)
		{
			if (id == itemId)
			{
				if (item.isWeapon())
					return Elementals.WEAPON_VALUES[12];
				return Elementals.ARMOR_VALUES[12];
			}
		}
		return 0;
	}
	
	public int getPowerToAdd(int stoneId, int oldValue, L2ItemInstance item)
	{
		boolean found = false;
		for (int id : Elementals.STONES)
		{
			if (id == stoneId)
			{
				found = true;
				break;
			}
		}
		if (!found)
		{
			for (int id : Elementals.ORES)
			{
				if (id == stoneId)
				{
					found = true;
					break;
				}
			}
		}
		if (!found)
		{
			for (int id : Elementals.CRYSTALS)
			{
				if (id == stoneId)
				{
					found = true;
					break;
				}
			}
		}
		if (!found)
		{
			for (int id : Elementals.JEWELS)
			{
				if (id == stoneId)
				{
					found = true;
					break;
				}
			}
		}
		if (!found)
		{
			for (int id : Elementals.ENERGIES)
			{
				if (id == stoneId)
				{
					found = true;
					break;
				}
			}
		}
		
		if (found)
		{
			if (item.isWeapon())
			{
				if (oldValue == 0)
					return Elementals.FIRST_WEAPON_BONUS;
				
				return Elementals.NEXT_WEAPON_BONUS;
			}
			else if (item.isArmor())
				return Elementals.ARMOR_BONUS;
		}
		
		return 0;
	}
	
	@Override
	public String getType()
	{
		return D0_38_REQUEST_EX_ENCHANT_ITEM_ATTRIBUTE;
	}
}
