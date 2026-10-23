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

import static com.l2jfree.gameserver.gameobjects.L2Npc.INTERACTION_DISTANCE;
import static com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory.MAX_ADENA;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Merchant;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2FishermanInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetManagerInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestSellItem extends L2ClientPacket
{
	private static final String _C__1E_REQUESTSELLITEM = "[C] 1E RequestSellItem";
	
	private static final int BATCH_LENGTH = 12; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 16;
	
	private int _listId;
	private Item[] _items = null;

	/** Creates one sale line; the wrapper verifies ownership and template identity before binding. */
	public static RequestSellItem forBridge(int listId, int objectId, int itemId, long count)
	{
		if (Config.MAX_ITEM_IN_PACKET < 1 || objectId <= 0 || itemId <= 0 || count <= 0)
			throw new IllegalArgumentException("cart");
		RequestSellItem packet = new RequestSellItem();
		packet._listId = listId;
		packet._items = new Item[] { packet.new Item(objectId, itemId, count) };
		return packet;
	}
	
	/**
	 * packet type id 0x1e
	 * 
	 * sample
	 * 
	 * 1e
	 * 00 00 00 00		// list id
	 * 02 00 00 00		// number of items
	 * 
	 * 71 72 00 10		// object id
	 * ea 05 00 00		// item id
	 * 01 00 00 00		// item count
	 * 
	 * 76 4b 00 10		// object id
	 * 2e 0a 00 00		// item id
	 * 01 00 00 00		// item count
	 * 
	 * format:		cdd (ddd)
	 */
	
	@Override
	protected void readImpl()
	{
		_listId = readD();
		int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		
		_items = new Item[count];
		for (int i = 0; i < count; i++)
		{
			int objectId = readD();
			int itemId = readD();
			long cnt = readCompQ();
			if (objectId < 1 || itemId < 1 || cnt < 1)
			{
				_items = null;
				return;
			}
			_items[i] = new Item(objectId, itemId, cnt);
		}
	}
	
	@Override
	protected void runImpl()
	{
		processSell();
	}
	
	protected void processSell()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_items == null)
		{
			sendAF();
			return;
		}
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			player.cancelActiveTrade();
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		L2Merchant merchant = player.getTarget(L2Merchant.class);
		String htmlFolder;
		if (merchant instanceof L2FishermanInstance)
			htmlFolder = "fisherman";
		else if (merchant instanceof L2PetManagerInstance)
			htmlFolder = "petmanager";
		else
			htmlFolder = "merchant";
		
		if (!canShop(player, merchant))
		{
			sendAF();
			return;
		}
		
		if (merchant != null && _listId > 1000000) // lease
		{
			if (merchant.getTemplate().getNpcId() != _listId - 1000000)
			{
				sendAF();
				return;
			}
		}
		
		long totalPrice = 0;
		List<Item> selling = new ArrayList<Item>(_items.length);
		// The whole cart is priced before anything is destroyed. A later line over the cap
		// used to return after the earlier lines were already gone, and addAdena never ran.
		for (Item i : _items)
		{
			L2ItemInstance item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "sell");
			if (item == null || !item.isSellable())
				continue;
			
			long price = item.getReferencePrice() / 2;
			if (price < 0 || (price > 0 && i.getCount() > MAX_ADENA / price))
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			long linePrice = price * i.getCount();
			if (linePrice < 0 || totalPrice > MAX_ADENA - linePrice)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			totalPrice += linePrice;
			selling.add(i);
		}
		
		long paid = 0;
		for (Item i : selling)
		{
			L2ItemInstance item = player.checkItemManipulation(i.getObjectId(), i.getCount(), "sell");
			if (item == null || !item.isSellable())
				continue;
			
			long price = item.getReferencePrice() / 2;
			if (player.getInventory().destroyItem("Sell", i.getObjectId(), i.getCount(), player, null) == null)
				continue;
			paid += price * i.getCount();
		}
		player.addAdena("Sell", paid, (L2Creature)merchant, false);
		
		if (merchant != null)
		{
			String html =
					HtmCache.getInstance().getHtm("data/html/" + htmlFolder + "/" + merchant.getNpcId() + "-sold.htm");
			
			if (html != null)
			{
				NpcHtmlMessage soldMsg = new NpcHtmlMessage(merchant.getObjectId());
				soldMsg.setHtml(html.replaceAll("%objectId%", String.valueOf(merchant.getObjectId())));
				player.sendPacket(soldMsg);
			}
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		sendPacket(su);
		sendPacket(new ItemList(player, true));
		
		sendAF();
	}
	
	private boolean canShop(L2Player player, L2Merchant target)
	{
		if (player.isGM())
			return true;
		
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && player.getKarma() > 0)
			return false;
		
		if (target == null)
			return false;
		
		L2Creature merchant = (L2Creature)target;
		if (!player.isSameInstance(merchant))
			return false;
		
		if (!player.isInsideRadius(merchant, INTERACTION_DISTANCE, false, false))
		{
			player.sendPacket(SystemMessageId.TOO_FAR_FROM_NPC);
			return false;
		}
		
		return true;
	}
	
	private class Item
	{
		private final int _objectId;
		//		private final int _itemId;
		private final long _count;
		
		public Item(int objId, int id, long num)
		{
			_objectId = objId;
			//			_itemId = id;
			_count = num;
		}
		
		public int getObjectId()
		{
			return _objectId;
		}
		
		//		public int getItemId()
		//		{
		//			return _itemId;
		//		}
		
		public long getCount()
		{
			return _count;
		}
	}
	
	@Override
	public String getType()
	{
		return _C__1E_REQUESTSELLITEM;
	}
}
