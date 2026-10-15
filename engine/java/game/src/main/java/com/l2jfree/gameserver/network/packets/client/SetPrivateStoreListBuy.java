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

import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.MAX_ADENA;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreManageListBuy;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreMsgBuy;

/**
 * This class ...
 * 
 * @version $Revision: 1.2.2.1.2.5 $ $Date: 2005/03/27 15:29:30 $
 */
public class SetPrivateStoreListBuy extends L2ClientPacket
{
	private static final String _C__91_SETPRIVATESTORELISTBUY = "[C] 91 SetPrivateStoreListBuy";
	
	private static final int BATCH_LENGTH = 12; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 40;
	
	private Item[] _items = null;

	static long addCostWithinAdenaCap(long totalCost, long lineCost)
	{
		return lineCost < 0 || lineCost > MAX_ADENA - totalCost ? -1 : totalCost + lineCost;
	}
	
	@Override
	protected void readImpl()
	{
		int count = readD();
		if (count < 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		
		_items = new Item[count];
		for (int i = 0; i < count; i++)
		{
			int itemId = readD();
			/*_unk1=*/readH();//TODO: analyse this
			/*_unk2=*/readH();//TODO: analyse this
			long cnt = readCompQ();
			long price = readCompQ();
			
			if (itemId < 1 || cnt < 1 || price < 0)
			{
				_items = null;
				return;
			}
			if (Config.PACKET_FINAL)
			{
				readC(); // FE
				readD(); // FF 00 00 00
				readD(); // 00 00 00 00
				readB(new byte[7]); // Completely Unknown
			}
			_items[i] = new Item(itemId, cnt, price);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_items == null)
		{
			player.setPrivateStoreType(L2Player.STORE_PRIVATE_NONE);
			player.broadcastUserInfo();
			sendAF();
			return;
		}
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		if (Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			return;
		}
		
		TradeList tradeList = player.getBuyList();
		tradeList.clear();
		
		// Check maximum number of allowed slots for pvt shops
		if (_items.length > player.getPrivateBuyStoreLimit())
		{
			player.sendPacket(new PrivateStoreManageListBuy(player));
			requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
			return;
		}
		
		long totalCost = 0;
		for (Item i : _items)
		{
			if (!i.addToTradeList(tradeList))
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			
			totalCost = addCostWithinAdenaCap(totalCost, i.getCost());
			if (totalCost < 0)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
		}
		
		// Check for available funds
		if (totalCost > player.getAdena())
		{
			player.sendPacket(new PrivateStoreManageListBuy(player));
			requestFailed(SystemMessageId.THE_PURCHASE_PRICE_IS_HIGHER_THAN_MONEY);
			return;
		}
		
		// Prevents player to start buying inside a nostore zone. By heX1r0
		if (player.isInsideZone(L2Zone.FLAG_NOSTORE))
		{
			player.sendPacket(new PrivateStoreManageListBuy(player));
			requestFailed(SystemMessageId.NO_PRIVATE_STORE_HERE);
			return;
		}
		
		player.sitDown();
		player.setPrivateStoreType(L2Player.STORE_PRIVATE_BUY);
		player.broadcastUserInfo();
		player.broadcastPacket(new PrivateStoreMsgBuy(player));
		
		sendAF();
	}
	
	private class Item
	{
		private final int _itemId;
		private final long _count;
		private final long _price;
		
		public Item(int id, long num, long pri)
		{
			_itemId = id;
			_count = num;
			_price = pri;
		}
		
		public boolean addToTradeList(TradeList list)
		{
			if ((MAX_ADENA / _count) < _price)
				return false;
			
			list.addItemByItemId(_itemId, _count, _price);
			return true;
		}
		
		public long getCost()
		{
			return _count * _price;
		}
	}
	
	@Override
	public String getType()
	{
		return _C__91_SETPRIVATESTORELISTBUY;
	}
}
