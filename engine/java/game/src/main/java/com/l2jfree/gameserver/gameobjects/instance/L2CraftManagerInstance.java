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
package com.l2jfree.gameserver.gameobjects.instance;

import java.util.ArrayList;
import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.RecipeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.recipe.L2RecipeInstance;
import com.l2jfree.gameserver.model.items.recipe.L2RecipeList;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

/**
 * @author G1ta0
 * Made in USSR
 */
public class L2CraftManagerInstance extends L2NpcInstance
{
	public static final int ITEMS_PER_PAGE = 5; // Items list size in craft and crystallize pages
	
	public L2CraftManagerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("multisell"))
		{
			int listId = Integer.parseInt(command.substring(9).trim());
			MultisellTable.getInstance().separateAndSend(listId, player, getNpcId(), false, getCastle().getTaxRate());
		}
		else if (command.startsWith("Crystallize")) // List player inventory items for crystallization
		{
			int _pageId;
			
			ArrayList<Integer> _itemsSelected = new ArrayList<Integer>();
			
			Inventory _inventory = player.getInventory();
			
			StringTokenizer st = new StringTokenizer(command.substring(11).trim());
			
			try
			{
				if (st.countTokens() > 1)
				{
					_pageId = Integer.parseInt(st.nextToken());
					
					while (st.hasMoreTokens())
					{
						int _itemObjId = Integer.parseInt(st.nextToken());
						
						if ((_inventory.getItemByObjectId(_itemObjId) != null)
								&& (!_itemsSelected.contains(_itemObjId)))
							_itemsSelected.add(_itemObjId);
					}
				}
				else
					_pageId = Integer.parseInt(command.substring(11).trim());
			}
			catch (NumberFormatException e)
			{
				_pageId = 0;
			}
			
			ArrayList<Integer> _items = new ArrayList<Integer>();
			
			int _priceTotal = 0;
			
			ArrayList<Integer> _crystals = new ArrayList<Integer>();
			
			_crystals.add(0, 0);
			_crystals.add(1, 0);
			_crystals.add(2, 0);
			_crystals.add(3, 0);
			_crystals.add(4, 0);
			_crystals.add(5, 0);
			
			for (L2ItemInstance _item : _inventory.getItems())
			{
				if (!_item.isStackable() && _item.getItem().getCrystalType() != L2Item.CRYSTAL_NONE
						&& _item.getItem().getCrystalCount() > 0 && !_item.isHeroItem())
				{
					_items.add(_item.getObjectId());
					
					if (_itemsSelected.contains(_item.getObjectId()))
					{
						int _count = _crystals.get(_item.getItem().getCrystalType()) + _item.getCrystalCount();
						
						_crystals.set(_item.getItem().getCrystalType(), _count);
						
						int _crystalId = 1457 + _item.getItem().getCrystalType();
						
						int _price =
								(int)(Config.ALT_CRAFT_PRICE * _count * ItemTable.getInstance().getTemplate(_crystalId)
										.getReferencePrice());
						if (_price == 0)
							_price = Config.ALT_CRAFT_DEFAULT_PRICE;
						
						_priceTotal += _price;
					}
				}
			}
			
			if (_items.size() == 0)
			{
				sendOutOfItems(player, "at least one", "breakable item");
				return;
			}
			
			int _itemsOnPage = ITEMS_PER_PAGE;
			int _pages = _items.size() / _itemsOnPage;
			
			if (_items.size() > _pages * _itemsOnPage)
				_pages++;
			if (_pageId > _pages)
				_pageId = _pages;
			
			int _itemStart = _pageId * _itemsOnPage;
			int _itemEnd = _items.size();
			
			if (_itemEnd - _itemStart > _itemsOnPage)
				_itemEnd = _itemStart + _itemsOnPage;
			
			String _elementsSelected = "";
			
			for (int i = 0; i < _itemsSelected.size(); i++)
				_elementsSelected += " " + _itemsSelected.get(i);
			
			NpcHtmlMessage npcReply = new NpcHtmlMessage(1);
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			
			replyMSG.append("<center>Items to Crystallize</center>");
			replyMSG.append("<img src=\"L2UI.SquareWhite\" width=270 height=1> <img src=\"L2UI.SquareBlank\" width=1 height=3>");
			replyMSG.append("<table width=270><tr>");
			replyMSG.append("<td width=66><button value=\"Back\" action=\"bypass -h npc_" + getObjectId()
					+ ((_pageId == 0) ? "_Chat 0" : "_Crystallize ") + (_pageId - 1) + _elementsSelected
					+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
			replyMSG.append("<td width=138></td>");
			replyMSG.append("<td width=66>"
					+ ((_pageId + 1 < _pages) ? "<button value=\"Next\" action=\"bypass -h npc_" + getObjectId()
							+ "_Crystallize " + (_pageId + 1) + _elementsSelected
							+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\">"
							: "") + "</td>");
			replyMSG.append("</tr></table>");
			replyMSG.append("<br><br>");
			replyMSG.append("<table width=270><tr>");
			if (Config.ALT_CRAFT_ALLOW_CRYSTALLIZE)
			{
				replyMSG.append("<td width=35><button value=\"\" action=\"bypass -h npc_" + getObjectId()
						+ "_BreakItem" + _elementsSelected
						+ "\" width=32 height=32 back=\"icon.skill0248\" fore=\"icon.skill0248\"></td>");
				replyMSG.append("<td width=135>");
				replyMSG.append("<table border=0 width=100%>");
				replyMSG.append("<tr><td><font color=\"B09878\">Cristallize</font></td></tr>");
				replyMSG.append("<tr><td><font color=\"B09878\">selected items "
						+ (_itemsSelected.size() == 0 ? "" : "(" + _itemsSelected.size() + ")")
						+ "</font></td></tr></table></td>");
				replyMSG.append("<td width=100>");
				replyMSG.append("<table border=0 width=100%>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">Total price:</font></td></tr>");
				replyMSG.append("<tr><td><font color=\"B09878\">" + _priceTotal
						+ " Adena</font></td></tr></table></td>");
			}
			replyMSG.append("</tr></table><br>");
			
			for (int i = _itemStart; i < _itemEnd; i++)
			{
				L2ItemInstance _item = _inventory.getItemByObjectId(_items.get(i));
				
				if (_item == null)
					continue;
				
				int _crystalId = 1457 + _item.getItem().getCrystalType();
				
				String _crystal =
						_item.getItem().getCrystalType() == 1 ? "D" : _item.getItem().getCrystalType() == 2 ? "C"
								: _item.getItem().getCrystalType() == 3 ? "B" : _item.getItem().getCrystalType() == 4
										? "A" : "S";
				
				int _count = _item.getCrystalCount();
				
				int _price =
						(int)(Config.ALT_CRAFT_PRICE * _count * ItemTable.getInstance().getTemplate(_crystalId)
								.getReferencePrice());
				if (_price == 0)
					_price = Config.ALT_CRAFT_DEFAULT_PRICE;
				
				replyMSG.append("<br><table width=270><tr>");
				replyMSG.append("<td width=35><img src=" + getCrystalIcon(_item.getItem().getCrystalType())
						+ " width=32 height=32 align=left></td>");
				replyMSG.append("<td width=215>");
				replyMSG.append("<table border=0 width=100%>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">"
						+ ItemTable.getInstance().getTemplate(_item.getItemId()).getName()
						+ (_item.getEnchantLevel() == 0 ? "" : " +" + _item.getEnchantLevel()) + "</font></td></tr>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">" + _crystal
						+ " Crystals:</font> <font color=\"B09878\">" + _count + "</font></td></tr></table></td>");
				
				if (Config.ALT_CRAFT_ALLOW_CRYSTALLIZE)
				{
					if (_itemsSelected.contains(_items.get(i)))
						replyMSG.append("<td valign=center width=20><button value=\"\" action=\"bypass -h npc_"
								+ getObjectId()
								+ "_Crystallize "
								+ _pageId
								+ _elementsSelected.replace(" " + _items.get(i).toString(), "")
								+ "\" width=16 height=16 back=\"L2UI.CheckBox_checked\" fore=\"L2UI.CheckBox_checked\"></td>");
					else
						replyMSG.append("<td valign=center width=20><button value=\"\" action=\"bypass -h npc_"
								+ getObjectId() + "_Crystallize " + _pageId + " " + _items.get(i).toString()
								+ _elementsSelected
								+ "\" width=16 height=16 back=\"L2UI.CheckBox\" fore=\"L2UI.CheckBox\"></td>");
				}
				else
					replyMSG.append("<td valign=center width=20></td>");
				
				replyMSG.append("</tr></table>");
			}
			
			replyMSG.append("</body></html>");
			
			npcReply.setHtml(replyMSG.toString());
			
			player.sendPacket(npcReply);
		}
		else if (command.startsWith("BreakItem") && Config.ALT_CRAFT_ALLOW_CRYSTALLIZE)
		// Crystallize selected items
		{
			ArrayList<Integer> _itemsSelected = new ArrayList<Integer>();
			
			Inventory _inventory = player.getInventory();
			
			StringTokenizer st = new StringTokenizer(command.substring(9).trim());
			
			try
			{
				while (st.hasMoreTokens())
				{
					int _itemObjId = Integer.parseInt(st.nextToken());
					
					if ((_inventory.getItemByObjectId(_itemObjId) != null) && (!_itemsSelected.contains(_itemObjId)))
						_itemsSelected.add(_itemObjId);
				}
			}
			catch (NumberFormatException e)
			{
			}
			
			if (_itemsSelected.size() == 0)
			{
				sendOutOfItems(player, "at least one", "breakable items");
				return;
			}
			
			int _priceTotal = 0;
			
			ArrayList<Integer> _crystals = new ArrayList<Integer>();
			
			_crystals.add(0, 0);
			_crystals.add(1, 0);
			_crystals.add(2, 0);
			_crystals.add(3, 0);
			_crystals.add(4, 0);
			_crystals.add(5, 0);
			
			for (int i = 0; i < _itemsSelected.size(); i++)
			{
				
				L2ItemInstance _item = _inventory.getItemByObjectId(_itemsSelected.get(i));
				
				if (_item != null && _item.getOwnerId() == player.getObjectId() && !_item.isStackable()
						&& _item.getItem().getCrystalType() != L2Item.CRYSTAL_NONE
						&& _item.getItem().getCrystalCount() > 0 && i < _itemsSelected.size())
				{
					int _count = _crystals.get(_item.getItem().getCrystalType()) + _item.getCrystalCount();
					
					_crystals.set(_item.getItem().getCrystalType(), _count);
					
					int _crystalId = 1457 + _item.getItem().getCrystalType();
					
					int _price =
							(int)(Config.ALT_CRAFT_PRICE * _count * ItemTable.getInstance().getTemplate(_crystalId)
									.getReferencePrice());
					if (_price == 0)
						_price = Config.ALT_CRAFT_DEFAULT_PRICE;
					
					_priceTotal += _price;
				}
				else
					_itemsSelected.remove(i);
			}
			
			if (_inventory.getInventoryItemCount(PlayerInventory.ADENA_ID, 0) < _priceTotal)
			{
				sendOutOfItems(player, Integer.toString(_priceTotal), "Adena");
				return;
			}
			
			InventoryUpdate iu = new InventoryUpdate();
			
			player.destroyItemByItemId("CraftManager", PlayerInventory.ADENA_ID, _priceTotal, player, true);
			iu.addModifiedItem(player.getInventory().getItemByItemId(PlayerInventory.ADENA_ID));
			
			for (int i = 0; i < _itemsSelected.size(); i++)
			{
				L2ItemInstance _item = _inventory.getItemByObjectId(_itemsSelected.get(i));
				
				if (_item != null && _item.getOwnerId() == player.getObjectId() && !_item.isStackable()
						&& _item.getItem().getCrystalType() != L2Item.CRYSTAL_NONE
						&& _item.getItem().getCrystalCount() > 0)
				{
					
					L2ItemInstance[] unequiped =
							player.getInventory().unEquipItemInSlotAndRecord(_item.getLocationSlot());
					
					if (_item.isEquipped())
						for (L2ItemInstance element : unequiped)
							iu.addModifiedItem(element);
					
					player.destroyItem("CraftManager", _itemsSelected.get(i), 1, player, true);
					iu.addModifiedItem(player.getInventory().getItemByObjectId(_itemsSelected.get(i)));
				}
			}
			
			for (int i = 0; i < _crystals.size(); i++)
			{
				if (_crystals.get(i) > 0)
				{
					int _crystalId = 1457 + i;
					
					SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
					sm.addItemName(_crystalId);
					sm.addNumber(_crystals.get(i));
					player.sendPacket(sm);
					
					_inventory.addItem("CraftManager", _crystalId, _crystals.get(i), player, player.getTarget());
					
					iu.addModifiedItem(player.getInventory().getItemByItemId(_crystalId));
				}
			}
			
			player.sendPacket(iu);
			player.broadcastUserInfo();
		}
		else if (command.startsWith("Manufacture"))
		// List recipes from player inventory
		
		{
			int _pageId;
			
			try
			{
				_pageId = Integer.parseInt(command.substring(11).trim());
			}
			catch (NumberFormatException e)
			{
				_pageId = 0;
			}
			
			Inventory _inventory = player.getInventory();
			
			ArrayList<Integer> _recipes = new ArrayList<Integer>();
			
			for (L2ItemInstance _item : _inventory.getItems())
			{
				if (_item.getItemType() == L2EtcItemType.RECEIPE)
				{
					L2RecipeList _recipe = RecipeTable.getInstance().getRecipeByItemId(_item.getItemId());
					
					if (_recipe != null)
						_recipes.add(_item.getObjectId());
				}
			}
			
			if (_recipes.size() == 0)
			{
				sendOutOfItems(player, "at least one", "recipe");
				return;
			}
			
			int _itemsOnPage = ITEMS_PER_PAGE;
			int _pages = _recipes.size() / _itemsOnPage;
			
			if (_recipes.size() > _pages * _itemsOnPage)
				_pages++;
			if (_pageId > _pages)
				_pageId = _pages;
			
			int _itemStart = _pageId * _itemsOnPage;
			int _itemEnd = _recipes.size();
			
			if (_itemEnd - _itemStart > _itemsOnPage)
				_itemEnd = _itemStart + _itemsOnPage;
			
			NpcHtmlMessage npcReply = new NpcHtmlMessage(1);
			
			StringBuilder replyMSG = new StringBuilder("<html><body>");
			
			replyMSG.append("<center>List of Recipes</center>");
			replyMSG.append("<img src=\"L2UI.SquareWhite\" width=270 height=1> <img src=\"L2UI.SquareBlank\" width=1 height=3>");
			replyMSG.append("<table width=270><tr>");
			replyMSG.append("<td width=66><button value=\"Back\" action=\"bypass -h npc_" + getObjectId()
					+ ((_pageId == 0) ? "_Chat 0" : "_Manufacture ") + (_pageId - 1)
					+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
			replyMSG.append("<td width=138></td>");
			replyMSG.append("<td width=66>"
					+ ((_pageId + 1 < _pages) ? "<button value=\"Next\" action=\"bypass -h npc_" + getObjectId()
							+ "_Manufacture " + (_pageId + 1)
							+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\">"
							: "") + "</td>");
			replyMSG.append("</tr></table>");
			replyMSG.append("<br>");
			
			for (int i = _itemStart; i < _itemEnd; i++)
			{
				L2ItemInstance _recipe = _inventory.getItemByObjectId(_recipes.get(i));
				
				if (_recipe == null)
					continue;
				
				L2RecipeList _recipeList = RecipeTable.getInstance().getRecipeByItemId(_recipe.getItemId());
				
				boolean _isConsumable = ItemTable.getInstance().getTemplate(_recipeList.getItemId()).isConsumable();
				
				int _price =
						(int)(Config.ALT_CRAFT_PRICE * _recipeList.getSuccessRate() / 100 * ItemTable.getInstance()
								.getTemplate(_recipeList.getItemId()).getReferencePrice());
				if (_price == 0)
					_price = Config.ALT_CRAFT_DEFAULT_PRICE;
				
				int _grade = ItemTable.getInstance().getTemplate(_recipeList.getItemId()).getCrystalType();
				
				String _recipeIcon =
						_recipeList.isDwarvenRecipe() ? getRecipeIcon(_grade) : "icon.etc_recipe_green_i00";
				
				replyMSG.append("<br>");
				replyMSG.append("<table width=270><tr>");
				replyMSG.append("<td valign=top width=35><button value=\"\" action=\"bypass -h npc_" + getObjectId()
						+ "_CraftInfo " + _recipes.get(i) + " 1 " + _pageId + "\" width=32 height=32 back=\""
						+ _recipeIcon + "\" fore=\"" + _recipeIcon + "\"></td>");
				replyMSG.append("<td valign=top width=235>");
				replyMSG.append("<table border=0 width=100%>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">"
						+ ItemTable.getInstance().getTemplate(_recipe.getItemId()).getName() + "</font></td></tr>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">Product:</font> <font color=\"B09878\">"
						+ (_isConsumable ? _recipeList.getCount() + " " : "")
						+ ItemTable.getInstance().getTemplate(_recipeList.getItemId()).getName()
						+ "</font></td></tr></table></td>");
				replyMSG.append("</tr></table>");
				replyMSG.append("<br>");
			}
			
			replyMSG.append("</body></html>");
			
			npcReply.setHtml(replyMSG.toString());
			
			player.sendPacket(npcReply);
		}
		else if (command.startsWith("CraftInfo"))
		// Show information about choosen recipe
		{
			int _recipeObjId = 0;
			int _pageId = 0;
			int _quantity = 1;
			
			StringTokenizer st = new StringTokenizer(command.substring(9).trim());
			
			try
			{
				if (st.countTokens() > 2)
				{
					_recipeObjId = Integer.parseInt(st.nextToken());
					_quantity = Integer.parseInt(st.nextToken());
				}
				else
					_recipeObjId = Integer.parseInt(st.nextToken());
				
				_pageId = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
			}
			
			Inventory _inventory = player.getInventory();
			
			L2ItemInstance _recipe = _inventory.getItemByObjectId(_recipeObjId);
			
			L2RecipeList _recipeList = RecipeTable.getInstance().getRecipeByItemId(_recipe.getItemId());
			
			boolean _isConsumable = ItemTable.getInstance().getTemplate(_recipeList.getItemId()).isConsumable();
			
			if (_recipe.getOwnerId() == player.getObjectId() && _recipe.getItemType() == L2EtcItemType.RECEIPE)
			{
				int _price =
						(int)(Config.ALT_CRAFT_PRICE * _recipeList.getSuccessRate() / 100 * _quantity
								* (_isConsumable ? _recipeList.getCount() : 1) * ItemTable.getInstance()
								.getTemplate(_recipeList.getItemId()).getReferencePrice());
				if (_price == 0)
					_price = Config.ALT_CRAFT_DEFAULT_PRICE;
				
				NpcHtmlMessage npcReply = new NpcHtmlMessage(1);
				
				StringBuilder replyMSG = new StringBuilder("<html><body>");
				
				replyMSG.append("<center>Craft Info</center>");
				replyMSG.append("<img src=\"L2UI.SquareWhite\" width=270 height=1> <img src=\"L2UI.SquareBlank\" width=1 height=3>");
				replyMSG.append("<table width=270><tr>");
				replyMSG.append("<td width=66><button value=\"Back\" action=\"bypass -h npc_" + getObjectId()
						+ "_Manufacture " + _pageId
						+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
				replyMSG.append("<td width=138></td>");
				replyMSG.append("<td width=66></td>");
				replyMSG.append("</tr></table>");
				replyMSG.append("<br>");
				replyMSG.append("<table width=270><tr>");
				
				if ((_recipeList.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_CRAFT)
						|| (!_recipeList.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_COMMON))
					replyMSG.append("<td valign=top width=35><button value=\"\" action=\"bypass -h npc_"
							+ getObjectId() + "_CraftItem " + _recipeObjId + " " + _quantity
							+ "\" width=32 height=32 back=\"icon.skill0172\" fore=\"icon.skill0172\"></td>");
				else
					replyMSG.append("<td valign=top width=35><img src=icon.skill0172 width=32 height=32 align=left></td>");
				
				replyMSG.append("<td valign=top width=235>");
				replyMSG.append("<table border=0 width=100%>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">"
						+ ItemTable.getInstance().getTemplate(_recipeList.getItemId()).getName() + "</font></td></tr>");
				replyMSG.append("<tr><td><font color=\"A2A0A2\">Product:</font> <font color=\"B09878\">"
						+ (_isConsumable ? _recipeList.getCount() * _quantity + " " : _quantity > 1 ? _quantity : "")
						+ ItemTable.getInstance().getTemplate(_recipeList.getItemId()).getName() + "</font></td></tr>");
				
				if ((_recipeList.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_CRAFT)
						|| (!_recipeList.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_COMMON))
					replyMSG.append("<tr><td><font color=\"A2A0A2\">Price:</font> <font color=\"B09878\">" + _price
							+ " Adena</font></td></tr></table></td>");
				else
					replyMSG.append("<tr><td></td></tr></table></td>");
				
				replyMSG.append("</tr></table>");
				replyMSG.append("<br>");
				replyMSG.append("<center>");
				replyMSG.append("<table width=210>");
				replyMSG.append("<tr><td valign=top width=70><font color=\"B09878\">Enter quantity:</font></td><td></td></tr>");
				replyMSG.append("<tr><td valign=top width=70><edit var=\"quantity\" width=70></td>");
				replyMSG.append("<td valign=top width=70><button value=\"Calculate\" action=\"bypass -h npc_"
						+ getObjectId() + "_CraftInfo " + _recipeObjId + " $quantity " + _pageId
						+ "\" width=66 height=16 back=\"L2UI.DefaultButton_click\" fore=\"L2UI.DefaultButton\"></td>");
				replyMSG.append("</tr></table>");
				replyMSG.append("</center>");
				replyMSG.append("<br>");
				replyMSG.append("<br>");
				replyMSG.append("<table width=270><tr>");
				replyMSG.append("<td width=220><font color=\"A2A0A2\">Ingredients</font></td>");
				replyMSG.append("<td width=50><font color=\"A2A0A2\">Quantity</font></td></tr>");
				
				L2RecipeInstance[] _recipeItems = _recipeList.getRecipes();
				
				for (L2RecipeInstance _recipeItem : _recipeItems)
				{
					L2ItemInstance _item = _inventory.getItemByItemId(_recipeItem.getItemId());
					
					String _quantityState =
							"<font color=\"55FF55\">" + _quantity * _recipeItem.getQuantity() + "</font>";
					
					if ((_item == null) || (_item.getCount() < _quantity * _recipeItem.getQuantity()))
						_quantityState =
								"<font color=\"FF5555\">"
										+ (int)(_quantity * _recipeItem.getQuantity() * Config.RATE_CRAFT_COST)
										+ "</font>";
					
					replyMSG.append("<tr><td width=220>"
							+ ItemTable.getInstance().getTemplate(_recipeItem.getItemId()).getName() + "</td>");
					replyMSG.append("<td width=50>" + _quantityState + "</td></tr>");
				}
				
				replyMSG.append("</table>");
				replyMSG.append("</body></html>");
				
				npcReply.setHtml(replyMSG.toString());
				player.sendPacket(npcReply);
			}
		}
		else if (command.startsWith("CraftItem") && (Config.ALT_CRAFT_ALLOW_CRAFT || Config.ALT_CRAFT_ALLOW_COMMON))
		// Craft amount of items using selected recipe
		{
			int _recipeObjId = 0;
			int _quantity = 1;
			
			StringTokenizer st = new StringTokenizer(command.substring(9).trim());
			
			if (st.countTokens() != 2)
				return;
			
			try
			{
				_recipeObjId = Integer.parseInt(st.nextToken());
				_quantity = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
			}
			
			Inventory _inventory = player.getInventory();
			
			L2ItemInstance _recipe = _inventory.getItemByObjectId(_recipeObjId);
			
			L2RecipeList _recipeList = RecipeTable.getInstance().getRecipeByItemId(_recipe.getItemId());
			
			boolean _isConsumable = ItemTable.getInstance().getTemplate(_recipeList.getItemId()).isConsumable();
			
			if (_recipe.getOwnerId() == player.getObjectId()
					&& _recipe.getItemType() == L2EtcItemType.RECEIPE
					&& ((_recipeList.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_CRAFT) || (!_recipeList
							.isDwarvenRecipe() && Config.ALT_CRAFT_ALLOW_COMMON)))
			{
				L2RecipeInstance[] _recipeItems = _recipeList.getRecipes();
				
				boolean _enoughtMaterials = true;
				
				for (L2RecipeInstance _recipeItem : _recipeItems)
				{
					L2ItemInstance _item = _inventory.getItemByItemId(_recipeItem.getItemId());
					if ((_item == null)
							|| (_item.getCount() < (int)(_quantity * _recipeItem.getQuantity() * Config.RATE_CRAFT_COST)))
						_enoughtMaterials = false;
				}
				
				int _price =
						(int)(Config.ALT_CRAFT_PRICE * _recipeList.getSuccessRate() / 100 * _quantity
								* _recipeList.getCount() * ItemTable.getInstance().getTemplate(_recipeList.getItemId())
								.getReferencePrice());
				if (_price == 0)
					_price = Config.ALT_CRAFT_DEFAULT_PRICE;
				
				if (_inventory.getInventoryItemCount(PlayerInventory.ADENA_ID, 0) < _price)
				{
					sendOutOfItems(player, Integer.toString(_price), "Adena");
					return;
				}
				
				if (!_enoughtMaterials)
				{
					sendOutOfItems(player, "proper amount", "materials");
					return;
				}
				
				int _quantitySuccess = 0;
				
				for (int i = 0; i < _quantity; i++)
					if (Rnd.get(100) < _recipeList.getSuccessRate())
						_quantitySuccess++;
				
				InventoryUpdate iu = new InventoryUpdate();
				for (L2RecipeInstance _recipeItem : _recipeItems)
				{
					player.destroyItemByItemId("CraftManager", _recipeItem.getItemId(),
							(int)(_quantity * _recipeItem.getQuantity() * Config.RATE_CRAFT_COST), player, true);
					iu.addModifiedItem(player.getInventory().getItemByItemId(_recipeItem.getItemId()));
				}
				
				player.destroyItemByItemId("CraftManager", PlayerInventory.ADENA_ID, _price, player, true);
				iu.addModifiedItem(player.getInventory().getItemByItemId(PlayerInventory.ADENA_ID));
				
				if (_quantitySuccess > 0)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
					sm.addItemName(_recipeList.getItemId());
					sm.addNumber(_quantitySuccess * _recipeList.getCount());
					player.sendPacket(sm);
					sm = null;
					
					iu.addModifiedItem(player.getInventory().getItemByItemId(_recipeList.getItemId()));
					_inventory.addItem("CraftManager", _recipeList.getItemId(), _quantitySuccess
							* (_isConsumable ? _recipeList.getCount() : 1), player, player.getTarget());
				}
				
				player.sendPacket(iu);
				iu = null;
				
				player.broadcastUserInfo();
				sendCraftedItems(player, _quantitySuccess * (_isConsumable ? _recipeList.getCount() : 1),
						(_quantity - _quantitySuccess) * (_isConsumable ? _recipeList.getCount() : 1), ItemTable
								.getInstance().getTemplate(_recipeList.getItemId()).getName());
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	public String getRecipeIcon(int grade)
	{
		return "icon.etc_recipe_"
				+ (grade == 1 ? "blue" : grade == 2 ? "yellow" : grade == 3 ? "red" : grade == 4 ? "violet"
						: grade == 5 ? "black" : "white") + "_i00";
	}
	
	public String getCrystalIcon(int grade)
	{
		return "icon.etc_crystal_"
				+ (grade == 1 ? "blue" : grade == 2 ? "green" : grade == 3 ? "red" : grade == 4 ? "silver" : "gold")
				+ "_i00";
	}
	
	public void sendOutOfItems(L2Player player, String count, String itemname)
	{
		NpcHtmlMessage npcReply = new NpcHtmlMessage(1);
		
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		
		replyMSG.append(getName() + ":<br>");
		replyMSG.append("Come back later, when you have " + count + " of " + itemname + ".");
		replyMSG.append("</body></html>");
		
		npcReply.setHtml(replyMSG.toString());
		
		player.sendPacket(npcReply);
	}
	
	public void sendCraftedItems(L2Player player, int success, int failed, String itemname)
	{
		NpcHtmlMessage npcReply = new NpcHtmlMessage(1);
		
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		
		replyMSG.append(getName() + ":<br>");
		
		if (success == 0)
			replyMSG.append("I'm sorry, " + player.getName() + ", but all attempts to create <font color=\"LEVEL\">"
					+ itemname + "</font> failed. All your materials have been lost.");
		else if (failed == 0)
			replyMSG.append("Congratulations, " + player.getName() + ", I created " + success
					+ " <font color=\"LEVEL\">" + itemname + "</font> for you!");
		else
			replyMSG.append("Here you go, " + player.getName() + ", " + success + " <font color=\"LEVEL\">" + itemname
					+ "</font> successfully created, but " + failed + " broken while craft.");
		
		replyMSG.append("</body></html>");
		
		npcReply.setHtml(replyMSG.toString());
		
		player.sendPacket(npcReply);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		
		return "data/html/default/" + pom + ".htm";
	}
}
