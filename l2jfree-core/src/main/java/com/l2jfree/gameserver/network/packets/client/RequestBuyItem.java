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
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Merchant;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.instance.L2FishermanInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetManagerInstance;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.util.Util;

/**
 * This class represents a packet sent by the client when the player confirms his item
 * selection in a general shop (not exchange shop)
 * 
 * @version $Revision: 1.12.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestBuyItem extends L2ClientPacket
{
	private static final String _C__1F_REQUESTBUYITEM = "[C] 1F RequestBuyItem";
	
	private static final int BATCH_LENGTH = 8; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 12;
	
	private int _listId;
	private Item[] _items = null;
	
	/**
	 * packet type id 0x1f<br>
	 * <br>
	 * sample<br>
	 * <br>
	 * 1f<br>
	 * 44 22 02 01 // list id<br>
	 * 02 00 00 00 // items to buy<br>
	 * <br>
	 * 27 07 00 00 // item id<br>
	 * 06 00 00 00 // count<br>
	 * <br>
	 * 83 06 00 00<br>
	 * 01 00 00 00<br>
	 * <br>
	 * format: cdd (dd)
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
			int itemId = readD();
			long cnt = readCompQ();
			
			if (itemId < 1 || cnt < 1)
			{
				_items = null;
				return;
			}
			_items[i] = new Item(itemId, cnt);
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
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final L2Merchant merchant = player.getTarget(L2Merchant.class);
		final String htmlFolder;
		if (merchant instanceof L2FishermanInstance)
			htmlFolder = "fisherman";
		else if (merchant instanceof L2PetManagerInstance)
			htmlFolder = "petmanager";
		else
			htmlFolder = "merchant";
		
		if (!canShop(player, merchant))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		int npcId = -1;
		L2TradeList list = null;
		
		if (merchant != null)
		{
			npcId = merchant.getTemplate().getNpcId();
			
			List<L2TradeList> lists = TradeListTable.getInstance().getBuyListByNpcId(npcId);
			/*
			if (lists == null)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
						+ " sent a false BuyList list_id.", Config.DEFAULT_PUNISH);
				return;
			}
			*/
			
			if (!player.isGM())
			{
				if (lists == null)
				{
					Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account "
							+ player.getAccountName() + " sent a false BuyList list_id.", Config.DEFAULT_PUNISH);
					return;
				}
				for (L2TradeList tradeList : lists)
				{
					if (tradeList.getListId() == _listId)
						list = tradeList;
				}
			}
			else
				list = TradeListTable.getInstance().getBuyList(_listId);
		}
		else
			list = TradeListTable.getInstance().getBuyList(_listId);
		
		if (list == null)
		{
			if (!player.isGM())
			{
				Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account "
						+ player.getAccountName() + " sent a false BuyList list_id.", Config.DEFAULT_PUNISH);
				return;
			}
			else
				player.sendMessage("Buylist " + _listId + " empty or not exists.");
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (list.isGm() && !player.isGM())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			Util.handleIllegalPlayerAction(player,
					"Warning!! Character " + player.getName() + " of account " + player.getAccountName()
							+ " sent a modified packet to buy from gmshop.", Config.DEFAULT_PUNISH);
			return;
		}
		
		_listId = list.getListId();
		
		if (_listId > 1000000) // lease
		{
			if (npcId != -1 && npcId != _listId / 100)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		double taxRate = 1.0;
		if (merchant instanceof L2MerchantInstance && ((L2MerchantInstance)merchant).getIsInTown())
			taxRate = ((L2MerchantInstance)merchant).getCastle().getTaxRate();
		
		long taxedPriceTotal = 0;
		long taxTotal = 0;
		
		// Check for buylist validity and calculates summary values
		long slots = 0;
		long weight = 0;
		for (Item i : _items)
		{
			long price = -1;
			
			if (!list.containsItemId(i.getItemId()))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account "
						+ player.getAccountName() + " sent a false BuyList list_id.", Config.DEFAULT_PUNISH);
				return;
			}
			
			L2Item template = ItemTable.getInstance().getTemplate(i.getItemId());
			if (template == null)
				continue;
			
			if (!template.isStackable() && i.getCount() > 1)
			{
				//Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
				//		+ " tried to purchase invalid quantity of items at the same time.", Config.DEFAULT_PUNISH);
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			
			price = list.getPriceForItemId(i.getItemId());
			
			for (int item : MercTicketManager.getInstance().getItemIds())
			{
				if (i.getItemId() == item)
				{
					price *= Config.RATE_SIEGE_GUARDS_PRICE;
					break;
				}
			}
			
			if (price < 0)
			{
				_log.warn("Error, no price found .. wrong buylist?");
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (price == 0 && !player.isGM() && Config.ONLY_GM_ITEMS_FREE)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName() + " of account "
						+ player.getAccountName() + " tried buy item for 0 adena.", Config.DEFAULT_PUNISH);
				return;
			}
			if ((MAX_ADENA / i.getCount()) < price)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			
			long stackPrice = price * i.getCount();
			long taxedPrice = (long)(stackPrice * taxRate);
			long tax = taxedPrice - stackPrice;
			if (taxedPrice > MAX_ADENA)
			{
				requestFailed(SystemMessageId.YOU_HAVE_EXCEEDED_QUANTITY_THAT_CAN_BE_INPUTTED);
				return;
			}
			taxedPriceTotal += taxedPrice;
			taxTotal += tax;
			
			weight = Inventory.addLoad(weight, template.getWeight(), i.getCount());
			if (weight < 0)
			{
				requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
				return;
			}
			if (!template.isStackable())
				slots += i.getCount();
			else if (player.getInventory().getItemByItemId(i.getItemId()) == null)
				slots++;
		}
		
		if ((weight >= Integer.MAX_VALUE) || (weight < 0) || !player.getInventory().validateWeight((int)weight))
		{
			requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			return;
		}
		
		if ((slots >= Integer.MAX_VALUE) || (slots < 0) || !player.getInventory().validateCapacity((int)slots))
		{
			requestFailed(SystemMessageId.SLOTS_FULL);
			return;
		}
		
		// Reserve limited stock before any adena moves. A line that does not fit is not charged.
		List<Item> accepted = new ArrayList<Item>(_items.length);
		synchronized (list)
		{
			for (Item i : _items)
			{
				if (list.countDecrease(i.getItemId()) && !list.decreaseCount(i.getItemId(), i.getCount()))
					continue;
				accepted.add(i);
			}
		}
		if (accepted.isEmpty())
		{
			requestFailed(SystemMessageId.ITEM_OUT_OF_STOCK);
			return;
		}
		taxedPriceTotal = 0;
		taxTotal = 0;
		for (Item i : accepted)
		{
			long price = list.getPriceForItemId(i.getItemId());
			for (int ticket : MercTicketManager.getInstance().getItemIds())
			{
				if (i.getItemId() == ticket)
				{
					price = (long)(price * Config.RATE_SIEGE_GUARDS_PRICE);
					break;
				}
			}
			long stackPrice = price * i.getCount();
			long taxedPrice = (long)(stackPrice * taxRate);
			taxedPriceTotal += taxedPrice;
			taxTotal += taxedPrice - stackPrice;
		}
		
		boolean paid = false;
		if (!player.isGM() || (player.isGM() && (player.getAccessLevel() < Config.GM_FREE_SHOP)))
		{
			if ((taxedPriceTotal < 0) || !player.reduceAdena("Buy", taxedPriceTotal, (L2Creature)merchant, false))
			{
				releaseReserved(list, accepted);
				requestFailed(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
				return;
			}
			paid = true;
		}
		
		if (!player.isGM())
		{
			//  Charge buyer and add tax to castle treasury if not owned by npc clan
			if (merchant instanceof L2MerchantInstance && ((L2MerchantInstance)merchant).getIsInTown()
					&& ((L2MerchantInstance)merchant).getCastle().getOwnerId() > 0)
				((L2MerchantInstance)merchant).getCastle().addToTreasury(taxTotal);
		}
		//  Check if player is GM and buying from GM shop or have proper access level
		else if (list.isGm() && (player.getAccessLevel() < Config.GM_CREATE_ITEM))
		{
			releaseReserved(list, accepted);
			if (paid)
				player.getInventory().addAdena("Buy", taxedPriceTotal, player, (L2Creature)merchant);
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		// Proceed the purchase. Stock for these lines is already reserved.
		for (Item i : accepted)
		{
			player.getInventory().addItem(list.isGm() ? "GMShop" : "Buy", i.getItemId(), i.getCount(), player,
					(L2Creature)merchant);
		}
		
		if (merchant != null)
		{
			String html =
					HtmCache.getInstance().getHtm(
							"data/html/" + htmlFolder + "/" + merchant.getTemplate().getNpcId() + "-bought.htm");
			
			if (html != null)
			{
				NpcHtmlMessage boughtMsg = new NpcHtmlMessage(merchant.getObjectId());
				boughtMsg.setHtml(html.replaceAll("%objectId%", String.valueOf(merchant.getObjectId())));
				sendPacket(boughtMsg);
				boughtMsg = null;
			}
		}
		
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		sendPacket(su);
		su = null;
		sendPacket(new ItemList(player, true));
	}
	
	/** Returns limited units reserved for lines the buyer will not be charged for. */
	private static void releaseReserved(L2TradeList list, List<Item> accepted)
	{
		synchronized (list)
		{
			for (Item i : accepted)
			{
				if (list.countDecrease(i.getItemId()))
					list.increaseCount(i.getItemId(), i.getCount());
			}
		}
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
			sendPacket(SystemMessageId.TOO_FAR_FROM_NPC);
			return false;
		}
		
		return true;
	}
	
	private class Item
	{
		private final int _itemId;
		private final long _count;
		
		public Item(int id, long num)
		{
			_itemId = id;
			_count = num;
		}
		
		public int getItemId()
		{
			return _itemId;
		}
		
		public long getCount()
		{
			return _count;
		}
	}
	
	@Override
	public String getType()
	{
		return _C__1F_REQUESTBUYITEM;
	}
}
