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
package com.l2jfree.gameserver.model;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

public class L2TradeList
{
	private final List<L2ItemInstance> _items;
	private final int _listId;
	private boolean _gm;
	private boolean _custom;
	
	private int _npcId;
	
	public L2TradeList(int listId)
	{
		_items = new ArrayList<L2ItemInstance>();
		_listId = listId;
	}
	
	public void setNpcId(String id)
	{
		try
		{
			_gm = false;
			_npcId = Integer.parseInt(id);
		}
		catch (Exception e)
		{
			if (id.equalsIgnoreCase("gm"))
				_gm = true;
		}
	}
	
	public void setCustom(boolean custom)
	{
		_custom = custom;
	}
	
	public boolean isCustom()
	{
		return _custom;
	}
	
	public int getNpcId()
	{
		return _npcId;
	}
	
	public boolean isGm()
	{
		return _gm;
	}
	
	public void addItem(L2ItemInstance item)
	{
		_items.add(item);
	}
	
	public void replaceItem(int itemID, long price)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemID)
			{
				item.setPriceToSell(price);
			}
		}
	}
	
	public synchronized boolean decreaseCount(int itemID, long count)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemID)
			{
				long newCount = item.getCount() - count;
				if (newCount < 0)
					continue;
				
				item.setCount(newCount);
				return true;
			}
		}
		
		return false;
	}

	/** Puts reserved units back. The same lock as {@link #decreaseCount(int, long)}. */
	public synchronized void increaseCount(int itemID, long count)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemID)
			{
				item.setCount(item.getCount() + count);
				return;
			}
		}
	}
	
	public synchronized void restoreCount(int time)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getCountDecrease() && item.getRestoreTime() == time)
			{
				item.restoreInitCount();
			}
		}
	}
	
	public void removeItem(int itemID)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemID)
			{
				_items.remove(i);
			}
		}
	}
	
	/**
	 * @return Returns the listId.
	 */
	public int getListId()
	{
		return _listId;
	}
	
	/**
	 * @return Returns the items.
	 */
	public List<L2ItemInstance> getItems()
	{
		return _items;
	}
	
	public List<L2ItemInstance> getItems(int start, int end)
	{
		return _items.subList(start, end);
	}
	
	public long getPriceForItemId(int itemId)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemId)
			{
				return item.getPriceToSell();
			}
		}
		return -1;
	}
	
	public boolean countDecrease(int itemId)
	{
		for (int i = 0; i < _items.size(); i++)
		{
			L2ItemInstance item = _items.get(i);
			if (item.getItemId() == itemId)
			{
				return item.getCountDecrease();
			}
		}
		return false;
	}
	
	public boolean containsItemId(int itemId)
	{
		for (L2ItemInstance item : _items)
		{
			if (item.getItemId() == itemId)
				return true;
		}
		
		return false;
	}
}
