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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.model.GMAudit;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.UnknownItems;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.persistence.item.ItemRepository;

/**
 * @author Advi
 *
 */
public abstract class ItemContainer
{
	protected static final Logger _log = LoggerFactory.getLogger(ItemContainer.class);
	
	protected final List<L2ItemInstance> _items;
	
	protected ItemContainer()
	{
		_items = new ArrayList<L2ItemInstance>();
	}
	
	protected abstract L2Creature getOwner();
	
	protected abstract ItemLocation getBaseLocation();
	
	public String getName()
	{
		return "ItemContainer";
	}
	
	/**
	 * Returns the ownerID of the inventory
	 * @return int
	 */
	public int getOwnerId()
	{
		return getOwner() == null ? 0 : getOwner().getObjectId();
	}
	
	/**
	 * Returns the quantity of items in the inventory
	 * @return int
	 */
	public int getSize()
	{
		return _items.size();
	}
	
	/**
	 * Returns the list of items in inventory
	 * @return L2ItemInstance : items in inventory
	 */
	public L2ItemInstance[] getItems()
	{
		return _items.toArray(new L2ItemInstance[_items.size()]);
	}
	
	/**
	 * Returns the item from inventory by using its <B>itemId</B><BR><BR>
	 * 
	 * @param itemId : int designating the ID of the item
	 * @return L2ItemInstance designating the item or null if not found in inventory
	 */
	public L2ItemInstance getItemByItemId(int itemId)
	{
		for (L2ItemInstance item : _items)
			if (item != null && item.getItemId() == itemId)
				return item;
		
		return null;
	}
	
	/**
	 * Returns the item from inventory by using its <B>itemId</B><BR><BR>
	 * 
	 * @param itemId : int designating the ID of the item
	 * @param itemToIgnore : used during a loop, to avoid returning the same item
	 * @return L2ItemInstance designating the item or null if not found in inventory
	 */
	public L2ItemInstance getItemByItemId(int itemId, L2ItemInstance itemToIgnore)
	{
		for (L2ItemInstance item : _items)
			if (item != null && item.getItemId() == itemId && !item.equals(itemToIgnore))
				return item;
		
		return null;
	}
	
	/**
	 * Returns the item's list from inventory by using its <B>itemId</B><BR><BR>
	 * 
	 * @param itemId : int designating the ID of the item
	 * @return List<L2ItemInstance> designating the items list (empty list if not found)
	 */
	public List<L2ItemInstance> getItemsByItemId(int itemId)
	{
		List<L2ItemInstance> returnList = new ArrayList<L2ItemInstance>();
		for (L2ItemInstance item : _items)
		{
			if (item != null && item.getItemId() == itemId)
			{
				returnList.add(item);
			}
		}
		
		return returnList;
	}
	
	/**
	 * Returns item from inventory by using its <B>objectId</B>
	 * @param objectId : int designating the ID of the object
	 * @return L2ItemInstance designating the item or null if not found in inventory
	 */
	public L2ItemInstance getItemByObjectId(int objectId)
	{
		for (L2ItemInstance item : _items)
		{
			if (item != null && item.getObjectId() == objectId)
				return item;
		}
		return null;
	}
	
	/**
	 * Gets count of item in the inventory.
	 * @param itemId item to look for
	 * @param enchantLevel enchant level to match on, or -1 for ANY enchant level
	 * @param includeEq whether to include equipped items
	 * @return the number of items matching the above conditions
	 */
	public long getInventoryItemCount(int itemId, int enchantLevel, boolean includeEq)
	{
		long count = 0;
		for (L2ItemInstance item : _items)
		{
			if (item.getItemId() == itemId && ((item.getEnchantLevel() == enchantLevel) || (enchantLevel < 0)))
			{
				if (!includeEq && item.isEquipped())
					continue;
				//if (item.isAvailable((L2Player)getOwner(), true) || item.getItem().getType2() == 3)//available or quest item
				if (item.isStackable())
					count = item.getCount();
				else
					count++;
			}
		}
		return count;
	}
	
	public long getInventoryItemCount(int itemId, int enchantLevel)
	{
		return getInventoryItemCount(itemId, enchantLevel, true);
	}
	
	public long getAdena()
	{
		for (L2ItemInstance item : _items)
			if (item.getItemId() == PlayerInventory.ADENA_ID)
				return item.getCount();
		return 0;
	}
	
	/**
	* Adds item to inventory
	 * @param process : String Identifier of process triggering this action
	* @param item : L2ItemInstance to be added
	 * @param actor : L2Player Player requesting the item add
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	* @return L2ItemInstance corresponding to the new item or the updated item in inventory
	*/
	public L2ItemInstance addItem(String process, L2ItemInstance item, L2Player actor, L2Object reference)
	{
		L2ItemInstance olditem = getItemByItemId(item.getItemId());
		
		// If stackable item is found in inventory just add to current quantity
		if (olditem != null && olditem.isStackable())
		{
			long count = item.getCount();
			changeCountWithReceipt(olditem, process, count, actor, reference);
			olditem.setLastChange(L2ItemInstance.MODIFIED);
			
			// And destroys the item
			ItemTable.getInstance().destroyItem(process, item, actor, reference);
			item.updateDatabase();
			item = olditem;
			
			// Updates database
			if (item.getItemId() == PlayerInventory.ADENA_ID && count < 10000 * Config.RATE_DROP_ADENA)
			{
				// Small adena changes won't be saved to database all the time
				if (GameTimeManager.getGameTicks() % 5 == 0)
					item.updateDatabase();
			}
			else
				item.updateDatabase();
		}
		// If item hasn't be found in inventory, create new one
		else
		{
			item.setOwnerId(process, getOwnerId(), actor, reference);
			item.setLocation(getBaseLocation());
			item.setLastChange((L2ItemInstance.ADDED));
			
			// Add item in inventory
			long addedCount = item.getCount();
			addItem(item);
			recordReceipt(item, addedCount);
			
			// Updates database
			item.updateDatabase();
		}
		
		refreshWeight();
		return item;
	}
	
	/**
	 * Adds item to inventory
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be added
	 * @param count : long Quantity of items to be added
	 * @param actor : L2Player Player requesting the item add
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the new item or the updated item in inventory
	 */
	public L2ItemInstance addItem(String process, int itemId, long count, L2Player actor, L2Object reference)
	{
		L2ItemInstance item = getItemByItemId(itemId);
		
		// If stackable item is found in inventory just add to current quantity
		if (item != null && item.isStackable())
		{
			changeCountWithReceipt(item, process, count, actor, reference);
			item.setLastChange(L2ItemInstance.MODIFIED);
			// Updates database
			if (itemId == PlayerInventory.ADENA_ID && count < 10000 * Config.RATE_DROP_ADENA)
			{
				// Small adena changes won't be saved to database all the time
				if (GameTimeManager.getGameTicks() % 5 == 0)
					item.updateDatabase();
			}
			else
				item.updateDatabase();
		}
		// If item hasn't be found in inventory, create new one
		else
		{
			for (int i = 0; i < count; i++)
			{
				L2Item template = ItemTable.getInstance().getTemplate(itemId);
				if (template == null)
				{
					_log.warn((actor != null ? "[" + actor.getName() + "] " : "") + "Invalid ItemId requested: "
							+ itemId);
					return null;
				}
				
				item =
						ItemTable.getInstance().createItem(process, itemId, template.isStackable() ? count : 1, actor,
								reference);
				item.setOwnerId(getOwnerId());
				item.setLocation(getBaseLocation());
				item.setLastChange(L2ItemInstance.ADDED);
				
				// Add item in inventory
				addItem(item);
				recordReceipt(item, template.isStackable() ? count : 1);
				// Updates database
				item.updateDatabase();
				
				// If stackable, end loop as entire count is included in 1 instance of item
				if (template.isStackable())
					break;
			}
		}
		
		refreshWeight();
		return item;
	}
	
	/**
	 * Adds Wear/Try On item to inventory<BR><BR>
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be added
	 * @param actor : L2Player Player requesting the item add
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the new weared item
	 */
	public L2ItemInstance addWearItem(String process, int itemId, L2Player actor, L2Object reference)
	{
		// Surch the item in the inventory of the player
		L2ItemInstance item = getItemByItemId(itemId);
		
		// There is such item already in inventory
		if (item != null)
			return item;
		
		// Create and Init the L2ItemInstance corresponding to the Item Identifier and quantity
		// Add the L2ItemInstance object to _allObjects of L2world
		item = ItemTable.getInstance().createItem(process, itemId, 1, actor, reference);
		
		// Set Item Properties
		item.setWear(true); // "Try On" Item -> Don't save it in database
		item.setOwnerId(getOwnerId());
		item.setLocation(getBaseLocation());
		item.setLastChange((L2ItemInstance.ADDED));
		
		// Add item in inventory and equip it if necessary (item location defined)
		addItem(item);
		recordReceipt(item, 1);
		
		// Calculate the weight loaded by player
		refreshWeight();
		
		return item;
	}
	
	/**
	 * Transfers item to another inventory
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Identifier of the item to be transfered
	 * @param count : long Quantity of items to be transfered
	 * @param actor : L2Player Player requesting the item transfer
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the new item or the updated item in inventory
	 */
	public synchronized L2ItemInstance transferItem(String process, int objectId, long count, ItemContainer target,
			L2Player actor, L2Object reference)
	{
		if (target == null)
			return null;
		
		L2ItemInstance sourceitem = getItemByObjectId(objectId);
		if (sourceitem == null)
			return null;
		L2ItemInstance targetitem = sourceitem.isStackable() ? target.getItemByItemId(sourceitem.getItemId()) : null;
		
		// Do only log if item leaves it's owner
		if (actor.isGM() && target.getOwner() != actor) // target.getOwner() should never be null
		{
			try
			{
				// DaDummy: this way we log _every_ gmtransfer with all related info
				String command = target.getClass().getSimpleName();
				String targetName = "";
				if (target.getOwner() != null)
					targetName = target.getOwner().getName();
				String params =
						targetName + " - " + String.valueOf(count) + " - "
								+ String.valueOf(sourceitem.getEnchantLevel()) + " - "
								+ String.valueOf(sourceitem.getItemId()) + " - " + sourceitem.getItemName() + " - "
								+ String.valueOf(sourceitem.getObjectId());
				
				GMAudit.auditGMAction(actor, "transferitem", command, params);
			}
			catch (Exception e)
			{
				_log.error("Impossible to log gm action. Please report this error.", e);
			}
		}
		
		synchronized (sourceitem)
		{
			// check if this item still present in this container
			if (getItemByObjectId(objectId) != sourceitem)
				return null;
			
			// Check if requested quantity is available
			if (count > sourceitem.getCount())
				count = sourceitem.getCount();
			
			// If possible, move entire item object
			if (sourceitem.getCount() == count && targetitem == null)
			{
				if (removeItem(sourceitem))
					recordReceipt(sourceitem, -count);
				target.addItem(process, sourceitem, actor, reference);
				targetitem = sourceitem;
			}
			else
			{
				if (sourceitem.getCount() > count) // If possible, only update counts
				{
					changeCountWithReceipt(sourceitem, process, -count, actor, reference);
					sourceitem.setLastChange(L2ItemInstance.MODIFIED);
				}
				else
				// Otherwise destroy old item
				{
					if (removeItem(sourceitem))
						recordReceipt(sourceitem, -count);
					sourceitem.setLastChange(L2ItemInstance.REMOVED);
					ItemTable.getInstance().destroyItem(process, sourceitem, actor, reference);
				}
				
				if (targetitem != null) // If possible, only update counts
				{
					target.changeCountWithReceipt(targetitem, process, count, actor, reference);
					targetitem.setLastChange(L2ItemInstance.MODIFIED);
				}
				else
				// Otherwise add new item
				{
					targetitem = target.addItem(process, sourceitem.getItemId(), count, actor, reference);
					targetitem.setLastChange(L2ItemInstance.ADDED);
				}
			}
			
			// Updates database
			sourceitem.updateDatabase(true);
			if (targetitem != sourceitem)
				targetitem.updateDatabase();
			
			if (sourceitem.isAugmented())
				sourceitem.getAugmentation().removeBonus(actor);
			refreshWeight();
		}
		return targetitem;
	}
	
	/**
	 * Destroy item from inventory and updates database
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be destroyed
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the destroyed item or the updated item in inventory
	 */
	public L2ItemInstance destroyItem(String process, L2ItemInstance item, L2Player actor, L2Object reference)
	{
		return destroyItem(process, item, item.getCount(), actor, reference);
	}
	
	/**
	 * Destroy item from inventory and updates database
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be destroyed
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the destroyed item or the updated item in inventory
	 */
	public L2ItemInstance destroyItem(String process, L2ItemInstance item, long count, L2Player actor,
			L2Object reference)
	{
		synchronized (item)
		{
			// Adjust item quantity
			if (item.getCount() > count)
			{
				changeCountWithReceipt(item, process, -count, actor, reference);
				item.setLastChange(L2ItemInstance.MODIFIED);
				
				// don't update often for untraced items
				if (process != null || GameTimeManager.getGameTicks() % 10 == 0)
				{
					item.updateDatabase();
				}
				
				refreshWeight();
				
				return item;
			}
			
			if (item.getCount() < count)
				return null;
			
			boolean removed = removeItem(item);
			if (!removed)
				return null;
			recordReceipt(item, -count);
			
			ItemTable.getInstance().destroyItem(process, item, actor, reference);
			item.updateDatabase();
			refreshWeight();
		}
		
		return item;
	}
	
	/**
	 * Destroy item from inventory by using its <B>objectID</B> and updates database
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Instance identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the destroyed item or the updated item in inventory
	 */
	public L2ItemInstance destroyItem(String process, int objectId, long count, L2Player actor, L2Object reference)
	{
		L2ItemInstance item = getItemByObjectId(objectId);
		if (item == null)
		{
			return null;
		}
		return destroyItem(process, item, count, actor, reference);
	}
	
	/**
	 * Destroy item from inventory by using its <B>itemId</B> and updates database
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the destroyed item or the updated item in inventory
	 */
	public L2ItemInstance destroyItemByItemId(String process, int itemId, long count, L2Player actor,
			L2Object reference)
	{
		L2ItemInstance item = getItemByItemId(itemId);
		if (item == null)
		{
			return null;
		}
		return destroyItem(process, item, count, actor, reference);
	}
	
	/**
	 * Destroy all items from inventory and updates database
	 * @param process : String Identifier of process triggering this action
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public synchronized void destroyAllItems(String process, L2Player actor, L2Object reference)
	{
		// destroyItem() removes the item from _items, so walk a copy
		for (L2ItemInstance item : new ArrayList<L2ItemInstance>(_items))
			destroyItem(process, item, actor, reference);
	}
	
	/**
	 * Adds item to inventory for further adjustments.
	 * @param item : L2ItemInstance to be added from inventory
	 */
	protected synchronized void addItem(L2ItemInstance item)
	{
		_items.add(item);
	}
	
	/**
	 * Removes item from inventory for further adjustments.
	 * @param item : L2ItemInstance to be removed from inventory
	 */
	protected synchronized boolean removeItem(L2ItemInstance item)
	{
		return _items.remove(item);
	}

	private void changeCountWithReceipt(L2ItemInstance item, String process, long count, L2Player actor, L2Object reference)
	{
		long before = item.getCount();
		item.changeCount(process, count, actor, reference);
		recordReceipt(item, item.getCount() - before);
	}

	private void recordReceipt(L2ItemInstance item, long delta)
	{
		if (delta == 0 || !(this instanceof PlayerInventory)
				|| !com.l2jfree.gameserver.network.bridge.EffectLedger.isActive())
			return;
		L2Player owner = ((PlayerInventory)this).getOwner();
		com.l2jfree.gameserver.network.bridge.EffectLedger.record(owner, item.getItemId() == PlayerInventory.ADENA_ID
				? com.l2jfree.gameserver.network.bridge.BridgeEffect.coin(delta)
				: com.l2jfree.gameserver.network.bridge.BridgeEffect.item(item.getItemId(), item.getObjectId(), delta));
	}

	/** Coordinates a prepared exchange with additions and removals in this container. */
	public Object itemSetLock()
	{
		return this;
	}
	
	/**
	 * Refresh the weight of equipment loaded
	 */
	protected void refreshWeight()
	{
	}
	
	/**
	 * Delete item object from world
	 */
	public void deleteMe()
	{
		try
		{
			updateDatabase();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		List<L2Object> items = new ArrayList<L2Object>(_items);
		_items.clear();
		
		L2World.getInstance().removeObjects(items);
	}
	
	/**
	 * Update database with items in inventory
	 */
	public void updateDatabase()
	{
		if (getOwner() != null)
		{
			for (L2ItemInstance item : _items)
			{
				if (item != null)
					item.updateDatabase(true);
			}
		}
	}
	
	/**
	 * Get back items in container from database
	 */
	public void restore()
	{
		try
		{
			L2ItemInstance item;
			UnknownItems unknown = new UnknownItems();
			for (ItemRepository.ItemRow row : ItemRepository.getInstance().loadItems(null, getStoredOwnerKey(),
					getBaseLocation().name(), getBaseLocation().name()))
			{
				if (ItemTable.getInstance().getTemplate(row.itemTemplateId()) == null)
				{
					unknown.miss();
					continue;
				}
				item = L2ItemInstance.restoreFromDb(getOwnerId(), row);
				if (item == null)
					continue;
				
				L2World.getInstance().storeObject(item);
				
				L2Creature owner = getOwner(); // may be null for clan WH
				// If stackable item is found in inventory just add to current quantity
				if (item.isStackable() && getItemByItemId(item.getItemId()) != null)
					addItem("Restore", item, owner != null ? owner.getActingPlayer() : null, null);
				else
					addItem(item);
			}
			
			refreshWeight();
			if (unknown.warning() != null)
				_log.warn(unknown.warning());
		}
		catch (Exception e)
		{
			_log.warn("could not restore container:", e);
		}
	}

	/**
	 * @return the key of the owner as the item table stores it: the owner id, or for the inventory of a pet the control
	 *         item of the pet (the owner id of a pet inventory is the owner of the pet)
	 */
	protected int getStoredOwnerKey()
	{
		if (getOwner() instanceof L2PetInstance)
			return ((L2PetInstance)getOwner()).getControlItemId();

		return getOwnerId();
	}

	/**
	 * @param slots
	 */
	public boolean validateCapacity(int slots)
	{
		return true;
	}
	
	/**
	 * @param weight
	 */
	public boolean validateWeight(int weight)
	{
		return true;
	}
	
	public boolean validateWeight(long weight)
	{
		return weight >= 0 && weight <= Integer.MAX_VALUE && validateWeight((int)weight);
	}
	
	public int getUnequippedSize()
	{
		int count = 0;
		for (L2ItemInstance temp : _items)
		{
			if (!temp.isEquipped())
				count++;
		}
		
		return count;
	}
}
