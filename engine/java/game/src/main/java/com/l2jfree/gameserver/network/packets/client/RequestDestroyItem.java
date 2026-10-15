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
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.persistence.item.ItemRepository;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;
import com.l2jfree.gameserver.util.Util;

/**
 * This class represents a packet sent by the client when a player drags an item over the
 * recycle bin
 * 
 * @version $Revision: 1.7.2.4.2.6 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestDestroyItem extends L2ClientPacket
{
	private static final String _C__REQUESTDESTROYITEM = "[C] 60 RequestDestroyItem c[dq]";
	
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
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		else if (!FloodProtector.tryPerformAction(activeChar, Protected.TRANSACTION))
			return;
		
		if (_count < 1)
		{
			requestFailed(SystemMessageId.CANNOT_DESTROY_NUMBER_INCORRECT);
			return;
		}
		
		if (activeChar.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			return;
		}
		
		L2ItemInstance itemToRemove = activeChar.getInventory().getItemByObjectId(_objectId);
		
		// if we can't find the requested item, its actually a cheat
		if (itemToRemove == null)
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return;
		}
		
		// Cannot discard item that the skill is consuming
		else if (activeChar.isCastingNow() && activeChar.getCurrentSkill() != null
				&& activeChar.getCurrentSkill().getSkill().getItemConsumeId() == itemToRemove.getItemId())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return;
		}
		
		// Cannot discard item that the skill is consuming
		else if (activeChar.isCastingSimultaneouslyNow() && activeChar.getLastSimultaneousSkillCast() != null
				&& activeChar.getLastSimultaneousSkillCast().getItemConsumeId() == itemToRemove.getItemId())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return;
		}
		
		int itemId = itemToRemove.getItemId();
		
		if (Config.ALT_STRICT_HERO_SYSTEM && itemToRemove.isHeroItem() && !activeChar.isGM())
		{
			requestFailed(SystemMessageId.HERO_WEAPONS_CANT_DESTROYED);
			return;
		}
		else if (itemToRemove.isWear()
				|| ((!itemToRemove.isDestroyable() || CursedWeaponsManager.getInstance().isCursed(itemId)) && !activeChar
						.isGM()))
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_THIS_ITEM);
			return;
		}
		
		if (!itemToRemove.isStackable() && _count > 1)
		{
			sendAF();
			Util.handleIllegalPlayerAction(activeChar,
					"[RequestDestroyItem] count > 1 but item is not stackable! oid: " + _objectId + " owner: "
							+ activeChar.getName(), Config.DEFAULT_PUNISH);
			return;
		}
		
		if (_count > itemToRemove.getCount())
		{
			requestFailed(SystemMessageId.CANNOT_DESTROY_NUMBER_INCORRECT);
			return;
			//_count = itemToRemove.getCount();
		}
		
		if (!GlobalRestrictions.canDestroyItem(activeChar, itemId, itemToRemove))
		{
			sendAF();
			return;
		}
		
		if (itemToRemove.isEquipped())
		{
			L2ItemInstance[] unequiped =
					activeChar.getInventory().unEquipItemInSlotAndRecord(itemToRemove.getLocationSlot());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequiped)
				iu.addModifiedItem(element);
			sendPacket(iu);
			activeChar.broadcastUserInfo();
		}
		
		if (PetDataTable.isPetItem(itemId))
		{
			try
			{
				if (activeChar.getPet() != null && activeChar.getPet().getControlItemId() == _objectId)
				{
					requestFailed(SystemMessageId.PET_SUMMONED_MAY_NOT_DESTROYED);
					return;
					//activeChar.getPet().unSummon(activeChar);
				}
				
				// if it's a pet control item, delete the pet
				ItemRepository.getInstance().deletePet(_objectId);
			}
			catch (Exception e)
			{
				_log.warn("Could not delete pet. ObjectId: ", e);
			}
		}
		
		if (itemToRemove.isTimeLimitedItem())
			itemToRemove.endOfLife();
		L2ItemInstance removedItem =
				activeChar.getInventory().destroyItem("Destroy", _objectId, _count, activeChar, null);
		if (removedItem == null)
		{
			sendAF();
			return;
		}
		activeChar.getInventory().updateInventory(removedItem);
		L2World.getInstance().removeObject(removedItem);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTDESTROYITEM;
	}
}
