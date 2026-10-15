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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format:(ch) d[dd]
 * 
 * @author -Wooden-
 */
public final class RequestSaveInventoryOrder extends L2ClientPacket
{
	private InventoryOrder[] _order;
	
	/** client limit */
	private static final int LIMIT = 125;
	
	@Override
	protected void readImpl()
	{
		int size = readD();
		final int limit = Math.min(size, LIMIT);
		
		_order = new InventoryOrder[limit];
		
		for (int i = 0; i < limit; i++)
		{
			int objectId = readD();
			int order = readD();
			_order[i] = new InventoryOrder(objectId, order);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		Inventory inventory = player.getInventory();
		for (InventoryOrder order : _order)
		{
			L2ItemInstance item = inventory.getItemByObjectId(order.objectID);
			if (item != null && item.getLocation() == ItemLocation.INVENTORY)
				item.setLocation(ItemLocation.INVENTORY, order.order);
		}
	}
	
	@Override
	public String getType()
	{
		return "[C] D0:49 RequestSaveInventoryOrder";
	}
	
	private static final class InventoryOrder
	{
		final int order;
		final int objectID;
		
		private InventoryOrder(int id, int ord)
		{
			objectID = id;
			order = ord;
		}
	}
}
