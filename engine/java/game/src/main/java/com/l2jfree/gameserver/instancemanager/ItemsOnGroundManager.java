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
package com.l2jfree.gameserver.instancemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.persistence.item.ItemRepository;

/**
 * This class manage all items on ground
 * 
 * @version $Revision: $ $Date: $
 * @author  DiezelMax - original ideea
 * @author  Enforcer  - actual build
 */
public class ItemsOnGroundManager
{
	protected static Logger _log = LoggerFactory.getLogger(ItemsOnGroundManager.class);
	
	protected List<L2ItemInstance> _items = null;
	
	private ItemsOnGroundManager()
	{
		if (!Config.SAVE_DROPPED_ITEM)
			return;
		_items = new CopyOnWriteArrayList<L2ItemInstance>();
		load();
		if (Config.SAVE_DROPPED_ITEM_INTERVAL > 0)
			ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new StoreInDb(),
					Config.SAVE_DROPPED_ITEM_INTERVAL, Config.SAVE_DROPPED_ITEM_INTERVAL);
	}
	
	public static final ItemsOnGroundManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private void load()
	{
		// If SaveDroppedItem is false, may want to delete all items previously stored to avoid add old items on reactivate
		if (!Config.SAVE_DROPPED_ITEM && Config.CLEAR_DROPPED_ITEM_TABLE)
			emptyTable();
		
		if (!Config.SAVE_DROPPED_ITEM)
			return;
		
		// if DestroyPlayerDroppedItem was previously  false, items curently protected will be added to ItemsAutoDestroy
		if (Config.DESTROY_DROPPED_PLAYER_ITEM)
		{
			try
			{
				// Recycle misc. items only, or all items including equipable
				ItemRepository.getInstance().recycleProtectedGroundItems(System.currentTimeMillis(),
						Config.DESTROY_EQUIPABLE_PLAYER_ITEM);
			}
			catch (Exception e)
			{
				_log.error("error while updating table ItemsOnGround " + e, e);
			}
		}
		
		// Add items to world
		try
		{
			int count = 0;
			for (ItemRepository.GroundItem row : ItemRepository.getInstance().loadGroundItems())
			{
				L2ItemInstance item = new L2ItemInstance(row.id(), row.itemTemplateId());
				L2World.getInstance().storeObject(item);
				item.setCount(row.count());
				item.setEnchantLevel(row.enchantLevel());
				item.getPosition().setXYZ(row.x(), row.y(), row.z());
				// A protected item has no drop time in the table; the item itself says -1
				item.setDropTime(row.isProtected() ? -1 : row.droppedAtMillis());
				item.setProtected(row.isProtected());
				L2World.getInstance().addVisibleObject(item);
				_items.add(item);
				count++;
				// Add to ItemsAutoDestroy only items not protected
				if (!row.isProtected())
				{
					ItemsAutoDestroyManager.tryAddItem(item);
				}
			}
			if (count > 0)
				_log.info("ItemsOnGroundManager: restored " + count + " items.");
			else
				_log.info("Initializing ItemsOnGroundManager.");
		}
		catch (Exception e)
		{
			_log.error("error while loading ItemsOnGround " + e, e);
		}
		
		if (Config.EMPTY_DROPPED_ITEM_TABLE_AFTER_LOAD)
			emptyTable();
	}
	
	public void save(L2ItemInstance item)
	{
		if (!Config.SAVE_DROPPED_ITEM)
			return;
		_items.add(item);
	}
	
	public void removeObject(L2Object item)
	{
		if (!Config.SAVE_DROPPED_ITEM)
			return;
		_items.remove(item);
	}
	
	public void saveInDb()
	{
		new StoreInDb().run();
	}
	
	public void cleanUp()
	{
		_items.clear();
	}
	
	public void emptyTable()
	{
		try
		{
			ItemRepository.getInstance().deleteGroundItems();
		}
		catch (Exception e1)
		{
			_log.error("error while cleaning table ItemsOnGround " + e1, e1);
		}
	}
	
	protected class StoreInDb extends Thread
	{
		@Override
		public void run()
		{
			if (!Config.SAVE_DROPPED_ITEM)
				return;
			
			if (_items.isEmpty() && _log.isDebugEnabled())
			{
				_log.warn("ItemsOnGroundManager: nothing to save...");
			}
			
			List<ItemRepository.GroundItem> rows = new ArrayList<ItemRepository.GroundItem>();
			for (L2ItemInstance item : _items)
			{
				if (item == null || item.getCount() <= 0)
					continue; // the table does not store an empty stack
				
				if (CursedWeaponsManager.getInstance().isCursed(item.getItemId()))
					continue; // Cursed Items not saved to ground, prevent double save
					
				// A protected item has no drop time in the table
				rows.add(new ItemRepository.GroundItem(item.getObjectId(), item.getItemId(), item.getCount(), item
						.getEnchantLevel(), item.getX(), item.getY(), item.getZ(), item.isProtected() ? 0 : item
						.getDropTime(), item.isProtected(), item.isEquipable()));
			}
			
			// The table is rewritten as a whole: the old rows go and the new ones are stored in one transaction
			if (ItemRepository.getInstance().replaceGroundItems(rows))
			{
				if (_log.isDebugEnabled())
					_log.warn("ItemsOnGroundManager: " + _items.size() + " items on ground saved");
			}
			else
				_log.error("error while saving the items on ground to table ItemsOnGround");
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ItemsOnGroundManager _instance = new ItemsOnGroundManager();
	}
}
