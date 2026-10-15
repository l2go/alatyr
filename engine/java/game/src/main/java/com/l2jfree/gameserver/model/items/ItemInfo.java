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

import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.packets.L2ServerPacket.ElementalOwner;

/**
 * Get all information from L2ItemInstance to generate ItemInfo.<BR><BR>
 * 
 */
public class ItemInfo implements ElementalOwner
{
	/** Identifier of the L2ItemInstance */
	private final int _objectId;
	
	/** The L2Item template of the L2ItemInstance */
	private final L2Item _item;
	
	/** The level of enchant on the L2ItemInstance */
	private final int _enchant;
	
	/** The augmentation of the item */
	private int _augmentation;
	
	/** The quantity of L2ItemInstance */
	private final long _count;
	
	/** The price of the L2ItemInstance */
	private long _price;
	
	/** The custom L2ItemInstance types (used loto, race tickets) */
	private final int _type1;
	private final int _type2;
	
	/** If True the L2ItemInstance is equipped */
	private final int _equipped;
	
	/** The action to do clientside (1=ADD, 2=MODIFY, 3=REMOVE) */
	private int _change;
	
	/** The mana of this item */
	private final int _mana;
	private final int _time;
	
	private final int _location;
	
	private byte _elemAtkType = -2;
	private int _elemAtkPower = 0;
	private final int[] _elemDefAttr = { 0, 0, 0, 0, 0, 0 };
	
	/**
	 * Get all information from L2ItemInstance to generate ItemInfo.<BR><BR>
	 * 
	 */
	public ItemInfo(L2ItemInstance item)
	{
		// Get the Identifier of the L2ItemInstance
		_objectId = item.getObjectId();
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = item.getEnchantLevel();
		
		// Get the augmentation boni
		if (item.isAugmented())
			_augmentation = item.getAugmentation().getAugmentationId();
		else
			_augmentation = 0;
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getCustomType1();
		_type2 = item.getCustomType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = item.isEquipped() ? 1 : 0;
		
		// Get the action to do clientside
		switch (item.getLastChange())
		{
			case L2ItemInstance.ADDED:
				_change = 1;
				break;
			case L2ItemInstance.MODIFIED:
				_change = 2;
				break;
			case L2ItemInstance.REMOVED:
				_change = 3;
				break;
		}
		
		// Get shadow item mana
		_mana = item.getMana();
		
		_time = item.isTimeLimitedItem() ? (int)(item.getRemainingTime() / 1000) : -1;
		
		_location = item.getLocationSlot();
		
		_elemAtkType = item.getAttackElementType();
		_elemAtkPower = item.getAttackElementPower();
		for (byte i = 0; i < 6; i++)
			_elemDefAttr[i] = item.getElementDefAttr(i);
	}
	
	public ItemInfo(L2ItemInstance item, int change)
	{
		// Get the Identifier of the L2ItemInstance
		_objectId = item.getObjectId();
		
		// Get the L2Item of the L2ItemInstance
		_item = item.getItem();
		
		// Get the enchant level of the L2ItemInstance
		_enchant = item.getEnchantLevel();
		
		// Get the augmentation boni
		if (item.isAugmented())
			_augmentation = item.getAugmentation().getAugmentationId();
		else
			_augmentation = 0;
		
		// Get the quantity of the L2ItemInstance
		_count = item.getCount();
		
		// Get custom item types (used loto, race tickets)
		_type1 = item.getCustomType1();
		_type2 = item.getCustomType2();
		
		// Verify if the L2ItemInstance is equipped
		_equipped = item.isEquipped() ? 1 : 0;
		
		// Get the action to do clientside
		_change = change;
		
		// Get shadow item mana
		_mana = item.getMana();
		
		_time = item.isTimeLimitedItem() ? (int)(item.getRemainingTime() / 1000) : -1;
		
		_location = item.getLocationSlot();
		
		_elemAtkType = item.getAttackElementType();
		_elemAtkPower = item.getAttackElementPower();
		for (byte i = 0; i < 6; i++)
			_elemDefAttr[i] = item.getElementDefAttr(i);
	}
	
	public int getObjectId()
	{
		return _objectId;
	}
	
	public L2Item getItem()
	{
		return _item;
	}
	
	public int getEnchant()
	{
		return _enchant;
	}
	
	public int getAugemtationBonus()
	{
		return _augmentation;
	}
	
	public long getCount()
	{
		return _count;
	}
	
	public long getPrice()
	{
		return _price;
	}
	
	public int getCustomType1()
	{
		return _type1;
	}
	
	public int getCustomType2()
	{
		return _type2;
	}
	
	public int getEquipped()
	{
		return _equipped;
	}
	
	public int getChange()
	{
		return _change;
	}
	
	public int getMana()
	{
		return _mana;
	}
	
	public int getTime()
	{
		return _time;
	}
	
	public int getLocation()
	{
		return _location;
	}
	
	@Override
	public byte getAttackElementType()
	{
		return _elemAtkType;
	}
	
	@Override
	public int getAttackElementPower()
	{
		return _elemAtkPower;
	}
	
	@Override
	public int getElementDefAttr(byte i)
	{
		return _elemDefAttr[i];
	}
}
