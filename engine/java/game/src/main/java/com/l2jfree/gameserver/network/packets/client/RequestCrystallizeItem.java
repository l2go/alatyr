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

import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class represents a packet that is sent by the client when a player drags the item
 * on the crystallization hammer.
 * 
 * @version $Revision: 1.2.2.3.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestCrystallizeItem extends L2ClientPacket
{
	private static final String _C__72_REQUESTDCRYSTALLIZEITEM = "[C] 72 RequestCrystallizeItem";
	
	private int _objectId;
	private long _count;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_count = readCompQ();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.CREATEITEM))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		else if (_count < 1)
		{
			requestFailed(SystemMessageId.NOT_ENOUGH_ITEMS);
			return;
		}
		else if (activeChar.getPrivateStoreType() != 0 || activeChar.isInCrystallize())
		{
			requestFailed(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			return;
		}
		
		int skillLevel = activeChar.getSkillLevel(L2Skill.SKILL_CRYSTALLIZE);
		if (skillLevel < 1)
		{
			requestFailed(SystemMessageId.CRYSTALLIZE_LEVEL_TOO_LOW);
			return;
		}
		
		PlayerInventory inventory = activeChar.getInventory();
		L2ItemInstance item = inventory.getItemByObjectId(_objectId);
		
		int grade;
		if (item == null || item.isWear() || item.isShadowItem() || item.isTimeLimitedItem() || item.isHeroItem()
				|| !item.getItem().isCrystallizable() || item.getItem().getCrystalCount() <= 0
				|| (grade = item.getItem().getItemGradeSPlus()) == L2Item.CRYSTAL_NONE)
		{
			requestFailed(SystemMessageId.ITEM_CANNOT_CRYSTALLIZED);
			return;
		}
		
		if (_count > item.getCount())
			_count = item.getCount();
		
		// Check if the char can crystallize items and return if false;
		boolean canCrystallize = true;
		switch (grade)
		{
			case L2Item.CRYSTAL_C:
			{
				if (skillLevel < 2)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_B:
			{
				if (skillLevel < 3)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_A:
			{
				if (skillLevel < 4)
					canCrystallize = false;
				break;
			}
			case L2Item.CRYSTAL_S:
			{
				if (skillLevel < 5)
					canCrystallize = false;
				break;
			}
		}
		
		if (!canCrystallize)
		{
			requestFailed(SystemMessageId.CRYSTALLIZE_LEVEL_TOO_LOW);
			return;
		}
		
		activeChar.setInCrystallize(true);
		
		//unequip if needed
		if (item.isEquipped())
		{
			L2ItemInstance[] unequiped = inventory.unEquipItemInSlotAndRecord(item.getLocationSlot());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequiped)
				iu.addModifiedItem(element);
			sendPacket(iu);
			
			SystemMessage sm;
			if (item.getEnchantLevel() > 0)
			{
				sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				sm.addNumber(item.getEnchantLevel());
				sm.addItemName(item);
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(item);
			}
			sendPacket(sm);
		}
		
		// remove from inventory
		L2ItemInstance removedItem = inventory.destroyItem("Crystalize", _objectId, _count, activeChar, null);
		if (removedItem == null)
		{
			requestFailed(SystemMessageId.NOT_ENOUGH_ITEMS);
			return;
		}
		
		// is this really so?
		InventoryUpdate iu = new InventoryUpdate();
		iu.addRemovedItem(removedItem);
		activeChar.sendPacket(iu);
		
		// add crystals
		int crystalId = item.getItem().getCrystalItemId();
		long crystalAmount = item.getCrystalCount();
		L2ItemInstance createditem = inventory.addItem("Crystalize", crystalId, crystalAmount, activeChar, activeChar);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.S1_CRYSTALLIZED);
		sm.addItemName(removedItem);
		sendPacket(sm);
		
		sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
		sm.addItemName(createditem);
		sm.addItemNumber(crystalAmount);
		sendPacket(sm);
		
		L2World.getInstance().removeObject(removedItem);
		activeChar.broadcastUserInfo();
		activeChar.setInCrystallize(false);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__72_REQUESTDCRYSTALLIZEITEM;
	}
}
