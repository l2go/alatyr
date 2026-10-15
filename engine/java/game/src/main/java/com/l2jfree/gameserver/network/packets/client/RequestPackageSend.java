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

import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.ADENA_ID;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ItemContainer;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerFreight;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * @author -Wooden-
 */
public final class RequestPackageSend extends L2ClientPacket
{
	private static final String _C_9F_REQUESTPACKAGESEND = "[C] 9F RequestPackageSend";
	
	private int _objectID;
	
	private static final int BATCH_LENGTH = 8; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 12;
	
	private Item[] _items = null;
	
	@Override
	protected void readImpl()
	{
		_objectID = readD();
		int count = readD();
		if (count < 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		_items = new Item[count];
		for (int i = 0; i < count; i++)
		{
			int id = readD(); //this is some id sent in PackageSendableList
			long cnt = readCompQ();
			_items[i] = new Item(id, cnt);
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (_items == null || !Config.ALLOW_FREIGHT)
			return;
		
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		else if (!FloodProtector.tryPerformAction(player, Protected.TRANSACTION))
			return;
		
		// Alt game - Karma punishment
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && player.getKarma() > 0)
			return;
		
		// player attempts to send freight to the different account
		if (!player.getAccountChars().containsKey(_objectID))
			return;
		
		PlayerFreight freight = player.getDepositedFreight(_objectID);
		
		player.setActiveWarehouse(freight);
		ItemContainer warehouse = player.getActiveWarehouse();
		if (warehouse == null)
			return;
		
		L2Npc manager = player.getLastFolkNPC();
		if ((manager == null || !manager.isWarehouse() || !manager.canInteract(player)) && !player.isGM())
			return;
		
		if (warehouse instanceof PlayerFreight && Config.GM_DISABLE_TRANSACTION
				&& player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			player.sendMessage("Unsufficient privileges.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// get current tradelist if any
		if (player.getActiveTradeList() != null)
			return;
		
		// Freight price from config or normal price per item slot (30)
		long fee = _items.length * Config.ALT_GAME_FREIGHT_PRICE;
		long currentAdena = player.getAdena();
		int slots = 0;
		
		for (Item i : _items)
		{
			// Check validity of requested item
			L2ItemInstance item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "deposit");
			if (item == null)
			{
				_log.warn("Error depositing a warehouse object for char " + player.getName() + " (validity check)");
				return;
			}
			
			// Calculate needed adena and slots
			if (item.getItemId() == ADENA_ID)
				currentAdena -= i.getCount();
			if (!item.isStackable())
				slots += i.getCount();
			else if (warehouse.getItemByItemId(item.getItemId()) == null)
				slots++;
		}
		
		// Item Max Limit Check
		if (!warehouse.validateCapacity(slots))
		{
			sendPacket(SystemMessageId.WAREHOUSE_FULL);
			return;
		}
		
		// Check if enough adena and charge the fee
		if (currentAdena < fee || !player.reduceAdena(warehouse.getName(), fee, manager, false))
		{
			sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			return;
		}
		
		// Proceed to the transfer
		InventoryUpdate playerIU = Config.FORCE_INVENTORY_UPDATE ? null : new InventoryUpdate();
		for (Item i : _items)
		{
			L2ItemInstance oldItem = player.getInventory().getItemByObjectId(i.getObjectId());
			if (oldItem == null)
			{
				_log.warn("Error depositing a warehouse object for char " + player.getName() + " (olditem == null)");
				continue;
			}
			
			if (!oldItem.isDepositable(false) || !oldItem.isAvailable(player, true, false))
				continue;
			
			L2ItemInstance newItem =
					player.getInventory().transferItem(warehouse.getName(), i.getObjectId(), i.getCount(), warehouse,
							player, manager);
			if (newItem == null)
			{
				_log.warn("Error depositing a warehouse object for char " + player.getName() + " (newitem == null)");
				continue;
			}
			
			if (playerIU != null)
			{
				if (oldItem.getCount() > 0 && oldItem != newItem)
					playerIU.addModifiedItem(oldItem);
				else
					playerIU.addRemovedItem(oldItem);
			}
		}
		
		// Send updated item list to the player
		if (playerIU != null)
			player.sendPacket(playerIU);
		else
			player.sendPacket(new ItemList(player, false));
		
		// Update current load status on player
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		player.sendPacket(su);
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C_9F_REQUESTPACKAGESEND;
	}
	
	private class Item
	{
		private final int _objectId;
		private final long _count;
		
		public Item(int i, long c)
		{
			_objectId = i;
			_count = c;
		}
		
		public int getObjectId()
		{
			return _objectId;
		}
		
		public long getCount()
		{
			return _count;
		}
	}
}
