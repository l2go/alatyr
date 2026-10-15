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
package com.l2jfree.gameserver.model.items;


/**
 * Class explanation:
 * For item counting or checking purposes. When you don't want to modify inventory
 * class contains itemId, quantity, ownerId, referencePrice, but not objectId
 */
public class TempItem
{
	// 	No object id stored, this will be only "list" of items with it's owner
	private final int _itemId;
	private int _quantity;
	private final int _ownerId;
	private final int _referencePrice;
	private final String _itemName;
	
	/**
	 * @param item
	 * @param quantity of that item
	 */
	public TempItem(L2ItemInstance item, int quantity)
	{
		super();
		_itemId = item.getItemId();
		_quantity = quantity;
		_ownerId = item.getOwnerId();
		_itemName = item.getItem().getName();
		_referencePrice = item.getReferencePrice();
	}
	
	/**
	 * @return Returns the quantity.
	 */
	public int getQuantity()
	{
		return _quantity;
	}
	
	/**
	 * @param quantity The quantity to set.
	 */
	public void setQuantity(int quantity)
	{
		_quantity = quantity;
	}
	
	public int getReferencePrice()
	{
		return _referencePrice;
	}
	
	/**
	 * @return Returns the itemId.
	 */
	public int getItemId()
	{
		return _itemId;
	}
	
	/**
	 * @return Returns the ownerId.
	 */
	public int getOwnerId()
	{
		return _ownerId;
	}
	
	/**
	 * @return Returns the itemName.
	 */
	public String getItemName()
	{
		return _itemName;
	}
}
