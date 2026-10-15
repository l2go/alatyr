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
package com.l2jfree.gameserver.gameobjects.itemcontainer;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;

public class NpcInventory extends Inventory
{
	private final L2Npc _owner;
	
	public NpcInventory(L2Npc owner)
	{
		_owner = owner;
	}
	
	public void reset()
	{
		destroyAllItems("Reset", null, null);
		if (_owner.getTemplate().getSS() > 0)
			addItem("Reset", 1835, _owner.getTemplate().getSS(), null, null);
		if (_owner.getTemplate().getBSS() > 0)
			addItem("Reset", 3947, _owner.getTemplate().getBSS(), null, null);
	}
	
	@Override
	public L2Npc getOwner()
	{
		return _owner;
	}
	
	@Override
	protected ItemLocation getBaseLocation()
	{
		return ItemLocation.NPC;
	}
	
	@Override
	protected ItemLocation getEquipLocation()
	{
		return ItemLocation.NPC;
	}
	
	/**
	 * Returns the list of all items in inventory that have a given item id.
	 * @return L2ItemInstance[] : matching items from inventory
	 */
	public L2ItemInstance[] getAllItemsByItemId(int itemId)
	{
		List<L2ItemInstance> list = new ArrayList<L2ItemInstance>();
		for (L2ItemInstance item : _items)
		{
			if (item.getItemId() == itemId)
				list.add(item);
		}
		
		return list.toArray(new L2ItemInstance[list.size()]);
	}
	
	/**
	 * Refresh the weight of equipment loaded
	 */
	@Override
	public void refreshWeight()
	{
		// not needed
	}
	
	/**
	 * Get back items in inventory from database
	 */
	@Override
	public void restore()
	{
		// not needed
	}
	
	@Override
	public void updateInventory(L2ItemInstance newItem)
	{
		// not needed
	}
}
