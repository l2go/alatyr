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

import java.util.List;
import java.util.concurrent.Future;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2MercManagerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

/**
 * This class ...
 * 
 * @version $Revision: 1.12.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestWearItem extends L2ClientPacket
{
	private static final String _C__C6_REQUESTWEARITEM = "[C] C6 RequestWearItem";
	
	protected Future<?> _removeWearItemsTask;
	
	//private int _unknow;
	
	/** List of ItemID to Wear */
	private int _listId;
	
	/** Number of Item to Wear */
	private int _count;
	
	/** Table of ItemId containing all Item to Wear */
	private int[] _items;
	
	/** Player that request a Try on */
	protected L2Player _activeChar;
	
	private class RemoveWearItemsTask implements Runnable
	{
		@Override
		public void run()
		{
			_activeChar.destroyWearedItems("Wear", null, true);
		}
	}
	
	/**
	 * Decrypt the RequestWearItem Client->Server Packet and Create _items table containing all ItemID to Wear.<BR><BR>
	 * 
	 */
	@Override
	protected void readImpl()
	{
		// Read and Decrypt the RequestWearItem Client->Server Packet
		_activeChar = getClient().getActiveChar();
		/*_unknow = */readD();
		_listId = readD(); // List of ItemID to Wear
		_count = readD(); // Number of Item to Wear
		
		if (_count < 0)
			_count = 0;
		if (_count > 100)
			_count = 0; // prevent too long lists
			
		// Create _items table that will contain all ItemID to Wear
		_items = new int[_count];
		
		// Fill _items table with all ItemID to Wear
		for (int i = 0; i < _count; i++)
		{
			int itemId = readD();
			_items[i] = itemId;
		}
	}
	
	@Override
	protected void runImpl()
	{
		// Get the current player and return if null
		if (_activeChar == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			_activeChar.cancelActiveTrade();
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		// If Alternate rule Karma punishment is set to true, forbid Wear to player with Karma
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && _activeChar.getKarma() > 0)
		{
			sendAF();
			return;
		}
		
		// Check current target of the player and the INTERACTION_DISTANCE
		L2Object target = _activeChar.getTarget();
		if (!_activeChar.isGM() && (target == null // No target (ie GM Shop)
				|| !(target instanceof L2MerchantInstance || target instanceof L2MercManagerInstance) // Target not a merchant and not mercmanager
		|| !_activeChar.isInsideRadius(target, L2Npc.INTERACTION_DISTANCE, false, false) // Distance is too far
				))
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		L2TradeList list = null;
		
		// Get the current merchant targeted by the player
		L2MerchantInstance merchant = (target instanceof L2MerchantInstance) ? (L2MerchantInstance)target : null;
		
		List<L2TradeList> lists = TradeListTable.getInstance().getBuyListByNpcId(merchant.getNpcId());
		
		if (lists == null)
		{
			if (_activeChar.isGM())
				sendPacket(SystemMessageId.ID_NOT_EXIST);
			else
				sendPacket(SystemMessageId.NO_INVENTORY_CANNOT_PURCHASE);
			//Util.handleIllegalPlayerAction(_activeChar, "Warning!! Character " + _activeChar.getName() + " from account " + _activeChar.getAccountName() + " sent a false BuyList ID.", Config.DEFAULT_PUNISH);
			sendAF();
			return;
		}
		
		for (L2TradeList tradeList : lists)
			if (tradeList.getListId() == _listId)
				list = tradeList;
		
		if (list == null)
		{
			if (_activeChar.isGM())
				sendPacket(SystemMessageId.ID_NOT_EXIST);
			else
				sendPacket(SystemMessageId.NO_INVENTORY_CANNOT_PURCHASE);
			//Util.handleIllegalPlayerAction(_activeChar, "Warning!! Character " + _activeChar.getName() + " from account " + _activeChar.getAccountName() + " sent a false BuyList ID.", Config.DEFAULT_PUNISH);
			sendAF();
			return;
		}
		
		_listId = list.getListId();
		
		// Check if the quantity of Item to Wear
		if (_count < 1 || _listId >= 1000000)
		{
			sendAF();
			return;
		}
		
		// Total Price of the Try On
		long totalPrice = 0;
		
		// Check for buylist validity and calculates summary values
		int slots = 0;
		int weight = 0;
		
		for (int i = 0; i < _count; i++)
		{
			int itemId = _items[i];
			
			if (!list.containsItemId(itemId))
			{
				requestFailed(SystemMessageId.NO_INVENTORY_CANNOT_PURCHASE);
				//Util.handleIllegalPlayerAction(_activeChar, "Warning!! Character " + _activeChar.getName() + " from account "+_activeChar.getAccountName() + " tried to falsify buylist contents.", Config.DEFAULT_PUNISH);
				return;
			}
			
			L2Item template = ItemTable.getInstance().getTemplate(itemId);
			weight += template.getWeight();
			slots++;
			
			totalPrice += Config.WEAR_PRICE;
			if (totalPrice > PlayerInventory.MAX_ADENA)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
		}
		
		// Check the weight
		if (!_activeChar.getInventory().validateWeight(weight))
		{
			requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			return;
		}
		
		// Check the inventory capacity
		if (!_activeChar.getInventory().validateCapacity(slots))
		{
			requestFailed(SystemMessageId.SLOTS_FULL);
			return;
		}
		
		// Charge buyer and add tax to castle treasury if not owned by npc clan because a Try On is not Free
		if ((totalPrice < 0) || !_activeChar.reduceAdena("Wear", (int)totalPrice, _activeChar.getLastFolkNPC(), false))
		{
			requestFailed(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			return;
		}
		
		// Proceed the wear
		InventoryUpdate playerIU = new InventoryUpdate();
		for (int i = 0; i < _count; i++)
		{
			int itemId = _items[i];
			
			/* Already done. Verify and remove?
			if (!list.containsItemId(itemId))
			{
				Util.handleIllegalPlayerAction(_activeChar,"Warning!! Character "+_activeChar.getName()+" of account "+_activeChar.getAccountName()+" sent a false BuyList list_id.",Config.DEFAULT_PUNISH);
				return;
			}
			*/
			
			// If player doesn't own this item : Add this L2ItemInstance to Inventory and set properties lastchanged to ADDED and _wear to True
			// If player already own this item : Return its L2ItemInstance (will not be destroy because property _wear set to False)
			L2ItemInstance item = _activeChar.getInventory().addWearItem("Wear", itemId, _activeChar, merchant);
			
			// Equip player with this item (set its location)
			_activeChar.getInventory().equipItemAndRecord(item);
			
			// Add this Item in the InventoryUpdate Server->Client Packet
			playerIU.addItem(item);
		}
		
		// Send the InventoryUpdate Server->Client Packet to the player
		// Add Items in player inventory and equip them
		sendPacket(playerIU);
		
		// Send the StatusUpdate Server->Client Packet to the player with new CUR_LOAD (0x0e) information
		StatusUpdate su = new StatusUpdate(_activeChar.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, _activeChar.getCurrentLoad());
		sendPacket(su);
		
		// Send a Server->Client packet UserInfo to this L2Player and CharInfo to all L2Player in its _knownPlayers
		_activeChar.broadcastUserInfo();
		
		// All weared items should be removed in ALLOW_WEAR_DELAY sec.
		if (_removeWearItemsTask == null)
			_removeWearItemsTask =
					ThreadPoolManager.getInstance()
							.scheduleGeneral(new RemoveWearItemsTask(), Config.WEAR_DELAY * 1000);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__C6_REQUESTWEARITEM;
	}
}
