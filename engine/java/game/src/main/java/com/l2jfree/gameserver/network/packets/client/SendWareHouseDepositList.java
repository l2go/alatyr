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
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ItemContainer;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerWarehouse;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * This class ...
 *
 * 31  SendWareHouseDepositList  cd (dd)
 * 
 * @version $Revision: 1.3.4.5 $ $Date: 2005/04/11 10:06:09 $
 */
public class SendWareHouseDepositList extends L2ClientPacket
{
	private static final String _C__31_SENDWAREHOUSEDEPOSITLIST = "[C] 31 SendWareHouseDepositList";
	
	private static final int BATCH_LENGTH = 8; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 12;
	
	private WarehouseItem _items[] = null;
	
	@Override
	protected void readImpl()
	{
		final int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		
		_items = new WarehouseItem[count];
		for (int i = 0; i < count; i++)
		{
			int objId = readD();
			long cnt = readCompQ();
			
			if (objId < 1 || cnt < 0)
			{
				_items = null;
				return;
			}
			_items[i] = new WarehouseItem(objId, cnt);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		else if (!FloodProtector.tryPerformAction(player, Protected.TRANSACTION))
			return;
		
		if (_items == null)
		{
			sendAF();
			return;
		}
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		ItemContainer warehouse = player.getActiveWarehouse();
		if (warehouse == null)
		{
			requestFailed(SystemMessageId.TRY_AGAIN_LATER);
			return;
		}
		
		boolean isPrivate = warehouse instanceof PlayerWarehouse;
		
		L2Npc manager = player.getLastFolkNPC();
		if ((manager == null || !manager.isWarehouse() || !manager.canInteract(player)) && !player.isGM())
		{
			requestFailed(SystemMessageId.WAREHOUSE_TOO_FAR);
			return;
		}
		
		if (!isPrivate && Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			return;
		}
		
		if (player.getActiveEnchantItem() != null)
		{
			requestFailed(SystemMessageId.TRY_AGAIN_LATER);
			//Util.handleIllegalPlayerAction(player,"Player "+player.getName()+" tried to use enchant Exploit!", IllegalPlayerAction.PUNISH_KICKBAN);
			return;
		}
		
		// A deposit cannot proceed while the player is trading. Check before charging the fee.
		if (hasActiveTrade(player.getActiveTradeList()))
			return;

		// Alt game - Karma punishment
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && player.getKarma() > 0)
		{
			sendAF();
			return;
		}
		
		// Freight price from config or normal price per item slot (30)
		final long fee = _items.length * 30;
		long currentAdena = player.getAdena();
		int slots = 0;
		
		for (WarehouseItem i : _items)
		{
			L2ItemInstance item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "deposit");
			if (item == null)
			{
				requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
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
			requestFailed(SystemMessageId.WAREHOUSE_CAPACITY_EXCEEDED);
			return;
		}
		
		// Check if enough adena and charge the fee
		if (currentAdena < fee || !player.reduceAdena(warehouse.getName(), fee, manager, false))
		{
			requestFailed(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			return;
		}
		
		// Proceed to the transfer
		InventoryUpdate playerIU = Config.FORCE_INVENTORY_UPDATE ? null : new InventoryUpdate();
		for (WarehouseItem i : _items)
		{
			// Check validity of requested item
			L2ItemInstance oldItem = player.checkItemManipulation(i.getObjectId(), i.getCount(), "deposit");
			if (oldItem == null)
			{
				requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
				_log.warn("Error depositing a warehouse object for char " + player.getName() + " (olditem == null)");
				return;
			}
			
			if (!oldItem.isDepositable(isPrivate) || !oldItem.isAvailable(player, true, isPrivate))
				continue;
			
			final L2ItemInstance newItem =
					player.getInventory().transferItem(warehouse.getName(), i.getObjectId(), i.getCount(), warehouse,
							player, manager);
			if (newItem == null)
			{
				// continue instead of return
				//requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
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
		sendPacket(su);
		
		sendAF();
	}
	
	static boolean hasActiveTrade(final TradeList tradeList)
	{
		return tradeList != null;
	}

	private class WarehouseItem
	{
		private final int _objectId;
		private final long _count;
		
		public WarehouseItem(int id, long num)
		{
			_objectId = id;
			_count = num;
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
	
	@Override
	public String getType()
	{
		return _C__31_SENDWAREHOUSEDEPOSITLIST;
	}
}
