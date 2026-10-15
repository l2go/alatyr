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

import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2ArmorType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.PetItemList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class RequestPetUseItem extends L2ClientPacket
{
	private static final String _C__8A_REQUESTPETUSEITEM = "[C] 8a RequestPetUseItem";
	
	private int _objectId;
	
	/**
	 * packet type id 0x8a
	 * format:      cd
	 */
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		// todo implement me properly
		//readQ();
		//readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2PetInstance pet = (L2PetInstance)activeChar.getPet();
		if (pet == null)
		{
			sendAF();
			return;
		}
		
		L2ItemInstance item = pet.getInventory().getItemByObjectId(_objectId);
		if (item == null)
		{
			sendAF();
			return;
		}
		
		if (item.isWear())
		{
			requestFailed(SystemMessageId.PET_CANNOT_USE_ITEM);
			return;
		}
		
		if (activeChar.isAlikeDead() || pet.isDead())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addItemName(item);
			activeChar.sendPacket(sm);
			return;
		}
		
		if (_log.isDebugEnabled())
			_log.debug(activeChar.getObjectId() + ": pet use item " + _objectId);
		
		if (!item.isEquipped())
		{
			if (!item.getItem().checkCondition(pet))
				return;
		}
		
		if (item.getItem().getBodyPart() == L2Item.SLOT_NECK)
		{
			if (item.getItem().getItemType() == L2ArmorType.PET)
			{
				useItem(pet, item, activeChar);
				return;
			}
		}
		
		//check if the item matches the pet
		if ((PetDataTable.isWolf(pet.getNpcId()) && item.getItem().isForWolf())
				|| (PetDataTable.isHatchling(pet.getNpcId()) && item.getItem().isForHatchling())
				|| (PetDataTable.isBaby(pet.getNpcId()) && item.getItem().isForBabyPet())
				|| (PetDataTable.isStrider(pet.getNpcId()) && item.getItem().isForStrider())
				|| (PetDataTable.isEvolvedWolf(pet.getNpcId()) && item.getItem().isForEvolvedWolf())
				|| (PetDataTable.isImprovedBaby(pet.getNpcId()) && item.getItem().isForBabyPet()))
		{
			useItem(pet, item, activeChar);
			return;
		}
		
		if (ItemHandler.getInstance().hasItemHandler(item.getItemId(), item))
		{
			useItem(pet, item, activeChar);
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.PET_CANNOT_USE_ITEM);
		}
	}
	
	private synchronized void useItem(L2PetInstance pet, L2ItemInstance item, L2Player activeChar)
	{
		if (item.isEquipable())
		{
			if (item.isEquipped())
			{
				pet.getInventory().unEquipItemInSlot(item.getLocationSlot());
				switch (item.getItem().getBodyPart())
				{
					case L2Item.SLOT_R_HAND:
						pet.setWeapon(0);
						break;
					case L2Item.SLOT_CHEST:
						pet.setArmor(0);
						break;
					case L2Item.SLOT_NECK:
						pet.setJewel(0);
						break;
				}
			}
			else
			{
				pet.getInventory().equipItem(item);
				switch (item.getItem().getBodyPart())
				{
					case L2Item.SLOT_R_HAND:
						pet.setWeapon(item.getItemId());
						break;
					case L2Item.SLOT_CHEST:
						pet.setArmor(item.getItemId());
						break;
					case L2Item.SLOT_NECK:
						pet.setJewel(item.getItemId());
						break;
				}
			}
			
			PetItemList pil = new PetItemList(pet);
			activeChar.sendPacket(pil);
			
			pet.broadcastFullInfo();
		}
		else
		{
			if (ItemHandler.getInstance().useItem(item.getItemId(), pet, item, false))
			{
				pet.broadcastFullInfo();
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.clientpackets.ClientBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__8A_REQUESTPETUSEITEM;
	}
}
