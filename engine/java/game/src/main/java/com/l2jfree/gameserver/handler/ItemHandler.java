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
package com.l2jfree.gameserver.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.handler.items.BeastSoulShot;
import com.l2jfree.gameserver.handler.items.BeastSpice;
import com.l2jfree.gameserver.handler.items.BeastSpiritShot;
import com.l2jfree.gameserver.handler.items.BlessedSpiritShot;
import com.l2jfree.gameserver.handler.items.Book;
import com.l2jfree.gameserver.handler.items.ColorName;
import com.l2jfree.gameserver.handler.items.DoorKey;
import com.l2jfree.gameserver.handler.items.Elixir;
import com.l2jfree.gameserver.handler.items.EnchantAttribute;
import com.l2jfree.gameserver.handler.items.EnchantScrolls;
import com.l2jfree.gameserver.handler.items.ExtractableItems;
import com.l2jfree.gameserver.handler.items.FishShots;
import com.l2jfree.gameserver.handler.items.Harvester;
import com.l2jfree.gameserver.handler.items.ItemSkills;
import com.l2jfree.gameserver.handler.items.Maps;
import com.l2jfree.gameserver.handler.items.MercTicket;
import com.l2jfree.gameserver.handler.items.PetFood;
import com.l2jfree.gameserver.handler.items.Potions;
import com.l2jfree.gameserver.handler.items.Recipes;
import com.l2jfree.gameserver.handler.items.RollingDice;
import com.l2jfree.gameserver.handler.items.ScrollOfResurrection;
import com.l2jfree.gameserver.handler.items.Seed;
import com.l2jfree.gameserver.handler.items.SevenSignsRecord;
import com.l2jfree.gameserver.handler.items.SoulCrystals;
import com.l2jfree.gameserver.handler.items.SoulShots;
import com.l2jfree.gameserver.handler.items.SpecialXMas;
import com.l2jfree.gameserver.handler.items.SpiritShot;
import com.l2jfree.gameserver.handler.items.SummonItems;
import com.l2jfree.gameserver.handler.items.TeleportBookmark;
import com.l2jfree.gameserver.handler.items.WrappedPack;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2EtcItem;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.util.HandlerRegistry;
import com.l2jfree.util.NumberHandlerRegistry;

public final class ItemHandler
{
	private static final Logger _log = LoggerFactory.getLogger(ItemHandler.class);
	
	private static final class SingletonHolder
	{
		private static final ItemHandler INSTANCE = new ItemHandler();
	}
	
	public static ItemHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final NumberHandlerRegistry<IItemHandler> _byItemId = new NumberHandlerRegistry<IItemHandler>() {
		@Override
		protected String getName()
		{
			return "ItemHandlerByItemId";
		}
	};
	private final HandlerRegistry<String, IItemHandler> _byHandlerName = new HandlerRegistry<String, IItemHandler>() {
		@Override
		protected String getName()
		{
			return "ItemHandlerByHandlerName";
		}
	};
	
	private ItemHandler()
	{
		registerItemHandler(new BeastSoulShot());
		registerItemHandler(new BeastSpice());
		registerItemHandler(new BeastSpiritShot());
		registerItemHandler(new BlessedSpiritShot());
		registerItemHandler(new Book());
		registerItemHandler(new ColorName());
		registerItemHandler(new DoorKey());
		registerItemHandler(new Elixir());
		registerItemHandler(new EnchantAttribute());
		registerItemHandler(new EnchantScrolls());
		registerItemHandler(new ExtractableItems());
		registerItemHandler(new FishShots());
		registerItemHandler(new Harvester());
		registerItemHandler(new ItemSkills());
		registerItemHandler(new Maps());
		registerItemHandler(new MercTicket());
		registerItemHandler(new PetFood());
		registerItemHandler(new Potions());
		registerItemHandler(new Recipes());
		registerItemHandler(new RollingDice());
		registerItemHandler(new ScrollOfResurrection());
		registerItemHandler(new Seed());
		registerItemHandler(new SevenSignsRecord());
		registerItemHandler(new SoulCrystals());
		registerItemHandler(new SoulShots());
		registerItemHandler(new SpecialXMas());
		registerItemHandler(new SpiritShot());
		registerItemHandler(new SummonItems());
		registerItemHandler(new TeleportBookmark());
		registerItemHandler(new WrappedPack());
		
		_log.info("ItemHandler: Loaded " + _byHandlerName.size() + " handlers by handlerName.");
		
		for (L2Item item : ItemTable.getInstance().getAllTemplates())
		{
			if (!(item instanceof L2EtcItem))
				continue;
			
			final String handlerName = ((L2EtcItem)item).getHandlerName();
			
			if (handlerName.isEmpty())
				continue;
			
			final IItemHandler handlerByHandlerName = _byHandlerName.get(handlerName);
			
			if (handlerByHandlerName == null)
			{
				_log.warn("ItemHandler: Missing handler for '" + handlerName + "'!");
				continue;
			}
			
			_byItemId.register(item.getItemId(), handlerByHandlerName);
		}
		
		_log.info("ItemHandler: Loaded " + _byItemId.size() + " handlers by itemId.");
	}
	
	public void registerItemHandler(IItemHandler handler)
	{
		_byHandlerName.register(handler.getClass().getSimpleName().intern(), handler);
	}
	
	public boolean hasItemHandler(int itemId, L2ItemInstance item)
	{
		return get(itemId, item) != null;
	}
	
	public boolean useItem(int itemId, L2Playable playable, L2ItemInstance item)
	{
		return useItem(itemId, playable, item, true);
	}
	
	public boolean useItem(int itemId, L2Playable playable, L2ItemInstance item, boolean warn)
	{
		final IItemHandler handler = get(itemId, item);
		
		if (handler == null)
		{
			if (warn)
				_log.warn("No item handler registered for item ID " + itemId + ".");
			return false;
		}
		
		if (!GlobalRestrictions.canUseItemHandler(handler.getClass(), itemId, playable, item))
			return true;
		
		handler.useItem(playable, item);
		return true;
	}
	
	private IItemHandler get(int itemId, L2ItemInstance item)
	{
		return _byItemId.get(itemId);
	}
}
