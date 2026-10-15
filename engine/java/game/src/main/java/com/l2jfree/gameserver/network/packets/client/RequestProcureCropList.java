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

import java.util.HashMap;
import java.util.Map;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2ManorManagerInstance;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager.CropProcure;
import com.l2jfree.gameserver.model.L2Manor;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Format: (ch) d [dddd] d: size [ d obj id d item id d manor id d count ]
 * 
 * @author l3x
 * 
 */
public class RequestProcureCropList extends L2ClientPacket
{
	private static final String _C__D0_09_REQUESTPROCURECROPLIST = "[C] D0:09 RequestProcureCropList";
	
	private static final int BATCH_LENGTH = 16; // length of the one item
	private static final int BATCH_LENGTH_FINAL = 20;
	
	private Crop[] _items = null;
	
	static boolean matchesClaimedCrop(L2ItemInstance item, int claimedItemId, long count)
	{
		return item != null && item.getItemId() == claimedItemId && item.getCount() >= count;
	}

	static boolean needsAdditionalSlot(L2ItemInstance item, int rewardId)
	{
		return item == null || item.getItemId() != rewardId;
	}

	static int addCapacityRequirement(int total, long count, int perItem)
	{
		if (count < 0 || perItem < 0 || (perItem != 0 && count > (Integer.MAX_VALUE - total) / perItem))
			return -1;
		return total + (int)(count * perItem);
	}

	@Override
	protected void readImpl()
	{
		int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET
				|| count * (Config.PACKET_FINAL ? BATCH_LENGTH_FINAL : BATCH_LENGTH) != getByteBuffer().remaining())
		{
			return;
		}
		
		_items = new Crop[count];
		for (int i = 0; i < count; i++)
		{
			int objId = readD();
			int itemId = readD();
			int manorId = readD();
			long cnt = readCompQ();
			if (objId < 1 || itemId < 1 || manorId < 0 || cnt < 1)
			{
				_items = null;
				return;
			}
			_items[i] = new Crop(objId, itemId, manorId, cnt);
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
			sendAF();
			return;
		}
		
		L2Object manager = player.getTarget();
		
		if (!(manager instanceof L2ManorManagerInstance))
			manager = player.getLastFolkNPC();
		
		if (!(manager instanceof L2ManorManagerInstance))
		{
			requestFailed(SystemMessageId.TARGET_IS_INCORRECT);
			return;
		}
		
		if (!player.isInsideRadius(manager, INTERACTION_DISTANCE, false, false))
		{
			requestFailed(SystemMessageId.TOO_FAR_FROM_NPC);
			return;
		}
		
		int castleId = ((L2ManorManagerInstance)manager).getCastle().getCastleId();
		
		// Calculate summary values
		int slots = 0;
		int weight = 0;
		Map<Long, Long> reservedCrops = new HashMap<Long, Long>();
		
		for (Crop i : _items)
		{
			if (!i.getCrop())
				continue;
			long stockKey = (((long)i.getManorId()) << 32) ^ (i.getItemId() & 0xFFFFFFFFL);
			long reserved = reservedCrops.containsKey(stockKey) ? reservedCrops.get(stockKey) : 0;
			if (!ManorStockReservation.canReserve(reserved, i.getCount(), i.getAmount()))
				return;
			reservedCrops.put(stockKey, reserved + i.getCount());

			L2Item template = ItemTable.getInstance().getTemplate(i.getReward());
			long rewardPrice = template.getReferencePrice();
			if (rewardPrice <= 0)
				continue;
			long rewardItemCount = i.getPrice() / rewardPrice;
			if (rewardItemCount < 1)
				continue;
			weight = addCapacityRequirement(weight, rewardItemCount, template.getWeight());
			if (weight < 0)
			{
				requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
				return;
			}

			if (!template.isStackable())
				slots = addCapacityRequirement(slots, rewardItemCount, 1);
			else if (needsAdditionalSlot(player.getInventory().getItemByItemId(i.getReward()), i.getReward()))
				slots++;
			if (slots < 0)
			{
				requestFailed(SystemMessageId.SLOTS_FULL);
				return;
			}
		}
		
		if (!player.getInventory().validateWeight(weight))
		{
			requestFailed(SystemMessageId.WEIGHT_LIMIT_EXCEEDED);
			return;
		}
		
		if (!player.getInventory().validateCapacity(slots))
		{
			requestFailed(SystemMessageId.SLOTS_FULL);
			return;
		}
		
		// Proceed the purchase
		InventoryUpdate playerIU = new InventoryUpdate();
		
		for (Crop i : _items)
		{
			if (i.getReward() == 0)
				continue;
			
			long fee = i.getFee(castleId); // fee for selling to other manors
			long rewardPrice = ItemTable.getInstance().getTemplate(i.getReward()).getReferencePrice();
			if (rewardPrice <= 0)
				continue;
			
			long rewardItemCount = i.getPrice() / rewardPrice;
			
			if (rewardItemCount < 1)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.FAILED_IN_TRADING_S2_OF_CROP_S1);
				sm.addItemName(i.getItemId());
				sm.addItemNumber(i.getCount());
				sendPacket(sm);
				continue;
			}
			
			if (player.getInventory().getAdena() < fee)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.FAILED_IN_TRADING_S2_OF_CROP_S1);
				sm.addItemName(i.getItemId());
				sm.addItemNumber(i.getCount());
				sendPacket(sm);
				sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
				continue;
			}
			
			// Add item to Inventory and adjust update packet
			L2ItemInstance itemDel = null;
			L2ItemInstance itemAdd = null;
			
			// check if player have correct items count
			L2ItemInstance item = player.getInventory().getItemByObjectId(i.getObjectId());
			if (!matchesClaimedCrop(item, i.getItemId(), i.getCount()))
				continue;
			
			itemDel = player.getInventory().destroyItem("Manor", i.getObjectId(), i.getCount(), player, manager);
			if (itemDel == null)
				continue;
			
			if (fee > 0)
				player.getInventory().reduceAdena("Manor", fee, player, manager);
			
			i.setCrop();
			
			itemAdd = player.getInventory().addItem("Manor", i.getReward(), rewardItemCount, player, manager);
			if (itemAdd == null)
				continue;
			
			playerIU.addRemovedItem(itemDel);
			if (itemAdd.getCount() > rewardItemCount)
				playerIU.addModifiedItem(itemAdd);
			else
				playerIU.addNewItem(itemAdd);
			
			// Send System Messages
			SystemMessage sm = new SystemMessage(SystemMessageId.TRADED_S2_OF_CROP_S1);
			sm.addItemName(i.getItemId());
			sm.addItemNumber(i.getCount());
			sendPacket(sm);
			
			if (fee > 0)
			{
				sm = new SystemMessage(SystemMessageId.S1_ADENA_HAS_BEEN_WITHDRAWN_TO_PAY_FOR_PURCHASING_FEES);
				sm.addItemNumber(fee);
				sendPacket(sm);
			}
			
			sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(i.getItemId());
			sm.addItemNumber(i.getCount());
			sendPacket(sm);
			
			if (fee > 0)
			{
				sm = new SystemMessage(SystemMessageId.S1_ADENA_DISAPPEARED);
				sm.addItemNumber(fee);
				sendPacket(sm);
			}
			
			sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
			sm.addItemName(itemAdd);
			sm.addItemNumber(rewardItemCount);
			sendPacket(sm);
		}
		
		// Send update packets
		sendPacket(playerIU);
		
		StatusUpdate su = new StatusUpdate(player.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		sendPacket(su);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_09_REQUESTPROCURECROPLIST;
	}
	
	private class Crop
	{
		private final int _objectId;
		private final int _itemId;
		private final int _manorId;
		private final long _count;
		private int _reward = 0;
		private CropProcure _crop = null;
		
		public Crop(int obj, int id, int m, long num)
		{
			_objectId = obj;
			_itemId = id;
			_manorId = m;
			_count = num;
		}
		
		public int getObjectId()
		{
			return _objectId;
		}
		
		public int getItemId()
		{
			return _itemId;
		}
		
		public long getCount()
		{
			return _count;
		}
		
		public int getManorId()
		{
			return _manorId;
		}

		public long getAmount()
		{
			return _crop.getAmount();
		}

		public int getReward()
		{
			return _reward;
		}
		
		public long getPrice()
		{
			return _crop.getPrice() * _count;
		}
		
		public long getFee(int castleId)
		{
			if (_manorId == castleId)
				return 0;
			
			return (getPrice() / 100) * 5; // 5% fee for selling to other manor
		}
		
		public boolean getCrop()
		{
			try
			{
				_crop =
						CastleManager.getInstance().getCastleById(_manorId)
								.getCrop(_itemId, CastleManorManager.PERIOD_CURRENT);
			}
			catch (NullPointerException e)
			{
				return false;
			}
			if (_crop == null || _crop.getId() == 0 || _crop.getPrice() == 0)
				return false;
			
			if (_count > _crop.getAmount())
				return false;
			
			if ((MAX_ADENA / _count) < _crop.getPrice())
				return false;
			
			_reward = L2Manor.getInstance().getRewardItem(_itemId, _crop.getReward());
			return true;
		}
		
		public void setCrop()
		{
			_crop.setAmount(_crop.getAmount() - _count);
			if (Config.ALT_MANOR_SAVE_ALL_ACTIONS)
				CastleManager.getInstance().getCastleById(_manorId)
						.updateCrop(_itemId, _crop.getAmount(), CastleManorManager.PERIOD_CURRENT);
		}
	}
}
