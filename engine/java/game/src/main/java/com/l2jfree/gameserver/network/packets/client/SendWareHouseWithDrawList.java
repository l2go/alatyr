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
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ClanWarehouse;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ItemContainer;
import com.l2jfree.gameserver.model.clan.L2Clan;
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
 * 32  SendWareHouseWithDrawList  cd (dd)
 * WootenGil rox :P
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2005/03/29 23:15:16 $
 */
public class SendWareHouseWithDrawList extends L2ClientPacket
{
	private static final String _C__32_SENDWAREHOUSEWITHDRAWLIST = "[C] 32 SendWareHouseWithDrawList";
	
	private static final int BATCH_LENGTH = 8; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 12;
	
	private WarehouseItem _items[] = null;
	
	@Override
	protected void readImpl()
	{
		int count = readD();
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
		
		L2Npc manager = player.getLastFolkNPC();
		if ((manager == null || !manager.isWarehouse() || !player.isInsideRadius(manager, L2Npc.INTERACTION_DISTANCE,
				false, false)) && !player.isGM())
		{
			requestFailed(SystemMessageId.WAREHOUSE_TOO_FAR);
			return;
		}
		
		if (warehouse instanceof ClanWarehouse && Config.GM_DISABLE_TRANSACTION
				&& player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			return;
		}
		
		// Alt game - Karma punishment
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && player.getKarma() > 0)
		{
			sendAF();
			return;
		}
		
		if (Config.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH)
		{
			if (warehouse instanceof ClanWarehouse && !L2Clan.checkPrivileges(player, L2Clan.CP_CL_VIEW_WAREHOUSE))
			{
				requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
				return;
			}
		}
		else if (warehouse instanceof ClanWarehouse && !player.isClanLeader())
		{
			requestFailed(SystemMessageId.ONLY_THE_CLAN_LEADER_IS_ENABLED);
			return;
		}
		
		long weight = 0;
		int slots = 0;
		
		for (WarehouseItem i : _items)
		{
			// Calculate needed slots
			L2ItemInstance item = warehouse.getItemByObjectId(i.getObjectId());
			if (item == null || item.getCount() < i.getCount())
			{/*
				Util.handleIllegalPlayerAction(player, "Warning!! Character "
						+ player.getName() + " of account "
						+ player.getAccountName() + " tried to withdraw non-existent item from warehouse.",
						Config.DEFAULT_PUNISH);
				*/
				requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
				return;
			}
			
			weight = Inventory.addLoad(weight, item.getItem().getWeight(), i.getCount());
			if (weight < 0)
			{
				requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
				return;
			}
			if (!item.isStackable())
				slots += i.getCount();
			else if (player.getInventory().getItemByItemId(item.getItemId()) == null)
				slots++;
		}
		
		// Item Max Limit Check
		if (!player.getInventory().validateCapacity(slots))
		{
			requestFailed(SystemMessageId.SLOTS_FULL);
			return;
		}
		
		// Weight limit Check
		if (!player.getInventory().validateWeight(weight))
		{
			requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			return;
		}
		
		// Proceed to the transfer
		InventoryUpdate playerIU = Config.FORCE_INVENTORY_UPDATE ? null : new InventoryUpdate();
		for (WarehouseItem i : _items)
		{
			L2ItemInstance oldItem = warehouse.getItemByObjectId(i.getObjectId());
			if (oldItem == null || oldItem.getCount() < i.getCount())
			{
				requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
				_log.warn("Error withdrawing a warehouse object for char " + player.getName() + " (olditem == null)");
				return;
			}
			L2ItemInstance newItem =
					warehouse.transferItem(warehouse.getName(), i.getObjectId(), i.getCount(), player.getInventory(),
							player, manager);
			if (newItem == null)
			{
				requestFailed(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
				_log.warn("Error withdrawing a warehouse object for char " + player.getName() + " (newitem == null)");
				return;
			}
			
			if (playerIU != null)
			{
				if (newItem.getCount() > i.getCount())
					playerIU.addModifiedItem(newItem);
				else
					playerIU.addNewItem(newItem);
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
		return _C__32_SENDWAREHOUSEWITHDRAWLIST;
	}
}
