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
package com.l2jfree.gameserver.datatables;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.network.packets.server.MultiSellList;
import com.l2jfree.gameserver.util.Util;

/**
 * Multisell list manager
 */
public final class MultisellTable
{
	private static final Logger _log = LoggerFactory.getLogger(MultisellTable.class);
	
	public static MultisellTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, MultiSellListContainer> _entries = new HashMap<Integer, MultiSellListContainer>();
	
	private MultisellTable()
	{
		reload();
	}
	
	public void reload()
	{
		_entries.clear();
		parse();
		_log.info("L2Multisell: Loaded " + _entries.size() + " lists.");
	}
	
	public MultiSellListContainer getList(int id)
	{
		final MultiSellListContainer list = _entries.get(id);
		
		if (list != null)
			return list;
		
		_log.warn("[L2Multisell] can't find list with id: " + id);
		return null;
	}
	
	/**
	 * This will generate the multisell list for the items.  There exist various
	 * parameters in multisells that affect the way they will appear:
	 * 1) inventory only:
	 * 		* if true, only show items of the multisell for which the
	 * 		  "primary" ingredients are already in the player's inventory.  By "primary"
	 * 		  ingredients we mean weapon and armor.
	 * 		* if false, show the entire list.
	 * 2) maintain enchantment: presumably, only lists with "inventory only" set to true
	 * 		should sometimes have this as true.  This makes no sense otherwise...
	 * 		* If true, then the product will match the enchantment level of the ingredient.
	 * 		  if the player has multiple items that match the ingredient list but the enchantment
	 * 		  levels differ, then the entries need to be duplicated to show the products and
	 * 		  ingredients for each enchantment level.
	 * 		  For example: If the player has a crystal staff +1 and a crystal staff +3 and goes
	 * 		  to exchange it at the mammon, the list should have all exchange possibilities for
	 * 		  the +1 staff, followed by all possibilities for the +3 staff.
	 * 		* If false, then any level ingredient will be considered equal and product will always
	 * 		  be at +0
	 * 3) apply taxes: Uses the "taxIngredient" entry in order to add a certain amount of adena to the ingredients
	 */
	private MultiSellListContainer generateMultiSell(int listId, boolean inventoryOnly, L2Player player, int npcId,
			double taxRate)
	{
		MultiSellListContainer listTemplate = getList(listId);
		MultiSellListContainer list = new MultiSellListContainer();
		if (listTemplate == null)
			return list;
		//list = new MultiSellListContainer();
		list.setListId(listId);
		if (npcId != 0 && !listTemplate.checkNpcId(npcId))
			listTemplate.addNpcId(npcId);
		
		if (inventoryOnly)
		{
			if (player == null)
				return list;
			
			L2ItemInstance[] items;
			if (listTemplate.getMaintainEnchantment())
				items = player.getInventory().getUniqueItemsByEnchantLevel(false, false, false);
			else
				items = player.getInventory().getUniqueItems(false, false, false);
			
			int enchantLevel, elementId, elementValue, augmentId, fireVal, waterVal, windVal, earthVal, holyVal, darkVal, mana;
			for (L2ItemInstance item : items)
			{
				// only do the matchup on equipable items that are not currently equipped
				// so for each appropriate item, produce a set of entries for the multisell list.
				if (!item.isEquipped() && (item.getItem() instanceof L2Armor || item.getItem() instanceof L2Weapon))
				{
					enchantLevel = (listTemplate.getMaintainEnchantment() ? item.getEnchantLevel() : 0);
					augmentId =
							(listTemplate.getMaintainEnchantment() ? (item.getAugmentation() != null ? item
									.getAugmentation().getAugmentationId() : 0) : 0);
					elementId = (listTemplate.getMaintainEnchantment() ? item.getAttackElementType() : -2);
					elementValue = (listTemplate.getMaintainEnchantment() ? item.getAttackElementPower() : 0);
					fireVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.FIRE) : 0);
					waterVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.WATER) : 0);
					windVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.WIND) : 0);
					earthVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.EARTH) : 0);
					holyVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.HOLY) : 0);
					darkVal = (listTemplate.getMaintainEnchantment() ? item.getElementDefAttr(Elementals.DARK) : 0);
					mana = (listTemplate.getMaintainEnchantment() ? item.getMana() : 0/*guess*/);
					
					// loop through the entries to see which ones we wish to include
					for (MultiSellEntry ent : listTemplate.getEntries())
					{
						boolean doInclude = false;
						
						// check ingredients of this entry to see if it's an entry we'd like to include.
						for (MultiSellIngredient ing : ent.getIngredients())
						{
							if (item.getItemId() == ing.getItemId())
							{
								doInclude = true;
								break;
							}
						}
						
						// manipulate the ingredients of the template entry for this particular instance shown
						// i.e: Assign enchant levels and/or apply taxes as needed.
						if (doInclude)
							list.addEntry(prepareEntry(ent, listTemplate.getApplyTaxes(),
									listTemplate.getMaintainEnchantment(), enchantLevel, augmentId, elementId,
									elementValue, fireVal, waterVal, windVal, earthVal, holyVal, darkVal, mana, taxRate));
					}
				}
			} // end for each inventory item.
		} // end if "inventory-only"
		else
		// this is a list-all type
		{
			// if no taxes are applied, no modifications are needed
			for (MultiSellEntry ent : listTemplate.getEntries())
				list.addEntry(prepareEntry(ent, listTemplate.getApplyTaxes(), false, 0, 0, -2, 0, 0, 0, 0, 0, 0, 0,
						0/*guess*/, taxRate));
		}
		
		return list;
	}
	
	// Regarding taxation, the following is the case:
	// a) The taxes come out purely from the adena TaxIngredient
	// b) If the entry has no adena ingredients other than the taxIngredient, the resulting
	//    amount of adena is appended to the entry
	// c) If the entry already has adena as an entry, the taxIngredient is used in order to increase
	//	  the count for the existing adena ingredient
	private MultiSellEntry prepareEntry(MultiSellEntry templateEntry, boolean applyTaxes, boolean maintainEnchantment,
			int enchantLevel, int augmentId, int elementId, int elementValue, int fireValue, int waterValue,
			int windValue, int earthValue, int holyValue, int darkValue, int mana, double taxRate)
	{
		MultiSellEntry newEntry = new MultiSellEntry(templateEntry.getEntryId() * 100000 + enchantLevel);
		
		long adenaAmount = 0;
		
		for (MultiSellIngredient ing : templateEntry.getIngredients())
		{
			// load the ingredient from the template
			MultiSellIngredient newIngredient = new MultiSellIngredient(ing);
			
			// if taxes are to be applied, modify/add the adena count based on the template adena/ancient adena count
			if (ing.getItemId() == PlayerInventory.ADENA_ID && ing.isTaxIngredient())
			{
				if (applyTaxes)
					adenaAmount += Math.round(ing.getItemCount() * taxRate);
				continue; // do not adena yet, as non-taxIngredient adena entries might occur next (order not guaranteed)
			}
			else if (ing.getItemId() == PlayerInventory.ADENA_ID) // && !ing.isTaxIngredient()
			{
				adenaAmount += ing.getItemCount();
				continue; // do not adena yet, as taxIngredient adena entries might occur next (order not guaranteed)
			}
			// if it is an armor/weapon, modify the enchantment level appropriately, if necessary
			// not used for clan reputation and fame
			else if (maintainEnchantment && newIngredient.getItemId() > 0)
			{
				L2Item tempItem = ItemTable.getInstance().getTemplate(ing.getItemId());
				if (tempItem instanceof L2Armor || tempItem instanceof L2Weapon)
				{
					newIngredient.setEnchantmentLevel(enchantLevel);
					newIngredient.setAugmentId(augmentId);
					newIngredient.setElementId(elementId);
					newIngredient.setElementValue(elementValue);
					newIngredient.setFireValue(fireValue);
					newIngredient.setWaterValue(waterValue);
					newIngredient.setWindValue(windValue);
					newIngredient.setEarthValue(earthValue);
					newIngredient.setHolyValue(holyValue);
					newIngredient.setDarkValue(darkValue);
					newIngredient.setManaLeft(mana);
				}
			}
			
			// finally, add this ingredient to the entry
			newEntry.addIngredient(newIngredient);
		}
		
		// now add the adena, if any.
		if (adenaAmount > 0)
		{
			newEntry.addIngredient(new MultiSellIngredient(PlayerInventory.ADENA_ID, adenaAmount));
		}
		// Now modify the enchantment level of products, if necessary
		for (MultiSellIngredient ing : templateEntry.getProducts())
		{
			// load the ingredient from the template
			MultiSellIngredient newIngredient = new MultiSellIngredient(ing);
			
			if (maintainEnchantment)
			{
				// if it is an armor/weapon, modify the enchantment level appropriately
				// (note, if maintain enchantment is "false" this modification will result to a +0)
				L2Item tempItem = ItemTable.getInstance().getTemplate(ing.getItemId());
				if (tempItem instanceof L2Armor || tempItem instanceof L2Weapon)
				{
					newIngredient.setEnchantmentLevel(enchantLevel);
					newIngredient.setAugmentId(augmentId);
					newIngredient.setElementId(elementId);
					newIngredient.setElementValue(elementValue);
					newIngredient.setFireValue(fireValue);
					newIngredient.setWaterValue(waterValue);
					newIngredient.setWindValue(windValue);
					newIngredient.setEarthValue(earthValue);
					newIngredient.setHolyValue(holyValue);
					newIngredient.setDarkValue(darkValue);
					newIngredient.setManaLeft(mana);
				}
			}
			newEntry.addProduct(newIngredient);
		}
		return newEntry;
	}
	
	public void separateAndSend(int listId, L2Player player, int npcId, boolean inventoryOnly, double taxRate)
	{
		MultiSellListContainer list = generateMultiSell(listId, inventoryOnly, player, npcId, taxRate);
		MultiSellListContainer temp = new MultiSellListContainer();
		int page = 1;
		
		temp.setListId(list.getListId());
		
		for (MultiSellEntry e : list.getEntries())
		{
			if (temp.getEntries().size() == 40)
			{
				player.sendPacket(new MultiSellList(temp, page++, 0));
				temp = new MultiSellListContainer();
				temp.setListId(list.getListId());
			}
			temp.addEntry(e);
		}
		player.sendPacket(new MultiSellList(temp, page, 1));
	}
	
	public static final class MultiSellEntry
	{
		private final int _entryId;
		
		private final List<MultiSellIngredient> _products = new ArrayList<MultiSellIngredient>();
		private final List<MultiSellIngredient> _ingredients = new ArrayList<MultiSellIngredient>();
		
		public MultiSellEntry(int entryId)
		{
			_entryId = entryId;
		}
		
		/**
		 * @return Returns the entryId.
		 */
		public int getEntryId()
		{
			return _entryId;
		}
		
		/**
		 * @param product The product to add.
		 */
		public void addProduct(MultiSellIngredient product)
		{
			_products.add(product);
		}
		
		/**
		 * @return Returns the products.
		 */
		public List<MultiSellIngredient> getProducts()
		{
			return _products;
		}
		
		/**
		 * @param ingredient The ingredients to set.
		 */
		public void addIngredient(MultiSellIngredient ingredient)
		{
			_ingredients.add(ingredient);
		}
		
		/**
		 * @return Returns the ingredients.
		 */
		public List<MultiSellIngredient> getIngredients()
		{
			return _ingredients;
		}
		
		public int stackable()
		{
			for (MultiSellIngredient p : _products)
			{
				L2Item template = ItemTable.getInstance().getTemplate(p.getItemId());
				if (template != null && !template.isStackable())
					return 0;
			}
			return 1;
		}
	}
	
	public static final class MultiSellIngredient
	{
		private int _itemId, _enchantmentLevel, _manaLeft, _element, _elementVal, _augment, _fireVal, _waterVal,
				_windVal, _earthVal, _holyVal, _darkVal;
		private long _itemCount;
		private boolean _isTaxIngredient, _maintainIngredient;
		
		public MultiSellIngredient(int itemId, long itemCount)
		{
			this(itemId, itemCount, false, false);
		}
		
		public MultiSellIngredient(int itemId, long itemCount, boolean isTaxIngredient, boolean maintainIngredient)
		{
			this(itemId, itemCount, 0, 0, -2, 0, 0, 0, 0, 0, 0, 0, 0/*guess*/, isTaxIngredient, maintainIngredient);
		}
		
		public MultiSellIngredient(int itemId, long itemCount, int enchantmentLevel, int augmentId, int elementId,
				int elementVal, int fireVal, int waterVal, int windVal, int earthVal, int holyVal, int darkVal,
				int manaLeft, boolean isTaxIngredient, boolean maintainIngredient)
		{
			setItemId(itemId);
			setItemCount(itemCount);
			setEnchantmentLevel(enchantmentLevel);
			setIsTaxIngredient(isTaxIngredient);
			setMaintainIngredient(maintainIngredient);
			setAugmentId(augmentId);
			setElementId(elementId);
			setElementValue(elementVal);
			setFireValue(fireVal);
			setWaterValue(waterVal);
			setWindValue(windVal);
			setEarthValue(earthVal);
			setHolyValue(holyVal);
			setDarkValue(darkVal);
			setManaLeft(manaLeft);
		}
		
		public MultiSellIngredient(MultiSellIngredient e)
		{
			_itemId = e.getItemId();
			_itemCount = e.getItemCount();
			_enchantmentLevel = e.getEnchantmentLevel();
			_isTaxIngredient = e.isTaxIngredient();
			_maintainIngredient = e.getMaintainIngredient();
			_augment = e.getAugmentId();
			_element = e.getElementId();
			_elementVal = e.getElementVal();
			_fireVal = e.getFireVal();
			_waterVal = e.getWaterVal();
			_windVal = e.getWindVal();
			_earthVal = e.getEarthVal();
			_holyVal = e.getHolyVal();
			_darkVal = e.getDarkVal();
			_manaLeft = e.getManaLeft();
		}
		
		public void setAugmentId(int augment)
		{
			_augment = augment;
		}
		
		public void setElementId(int element)
		{
			_element = element;
		}
		
		public void setElementValue(int elementVal)
		{
			_elementVal = elementVal;
		}
		
		public void setFireValue(int val)
		{
			_fireVal = val;
		}
		
		public void setWaterValue(int val)
		{
			_waterVal = val;
		}
		
		public void setWindValue(int val)
		{
			_windVal = val;
		}
		
		public void setEarthValue(int val)
		{
			_earthVal = val;
		}
		
		public void setHolyValue(int val)
		{
			_holyVal = val;
		}
		
		public void setDarkValue(int val)
		{
			_darkVal = val;
		}
		
		public int getAugmentId()
		{
			return _augment;
		}
		
		public int getElementId()
		{
			return _element;
		}
		
		public int getElementVal()
		{
			return _elementVal;
		}
		
		public int getFireVal()
		{
			return _fireVal;
		}
		
		public int getWaterVal()
		{
			return _waterVal;
		}
		
		public int getWindVal()
		{
			return _windVal;
		}
		
		public int getEarthVal()
		{
			return _earthVal;
		}
		
		public int getHolyVal()
		{
			return _holyVal;
		}
		
		public int getDarkVal()
		{
			return _darkVal;
		}
		
		/**
		 * @param itemId The itemId to set.
		 */
		public void setItemId(int itemId)
		{
			_itemId = itemId;
		}
		
		/**
		 * @return Returns the itemId.
		 */
		public int getItemId()
		{
			return _itemId;
		}
		
		/**
		 * @param itemCount The itemCount to set.
		 */
		public void setItemCount(long itemCount)
		{
			_itemCount = itemCount;
		}
		
		/**
		 * @return Returns the itemCount.
		 */
		public long getItemCount()
		{
			return _itemCount;
		}
		
		/**
		 * @param enchantmentLevel The itemCount to set.
		 */
		public void setEnchantmentLevel(int enchantmentLevel)
		{
			_enchantmentLevel = enchantmentLevel;
		}
		
		/**
		 * @return Returns the itemCount.
		 */
		public int getEnchantmentLevel()
		{
			return _enchantmentLevel;
		}
		
		public void setIsTaxIngredient(boolean isTaxIngredient)
		{
			_isTaxIngredient = isTaxIngredient;
		}
		
		public boolean isTaxIngredient()
		{
			return _isTaxIngredient;
		}
		
		public void setMaintainIngredient(boolean maintainIngredient)
		{
			_maintainIngredient = maintainIngredient;
		}
		
		public boolean getMaintainIngredient()
		{
			return _maintainIngredient;
		}
		
		public int getManaLeft()
		{
			return _manaLeft;
		}
		
		public void setManaLeft(int mana)
		{
			_manaLeft = mana;
		}
	}
	
	public static final class MultiSellListContainer
	{
		private int _listId;
		private boolean _applyTaxes = false;
		private boolean _maintainEnchantment = false;
		private List<Integer> _npcIds;
		
		private final List<MultiSellEntry> _entriesC = new ArrayList<MultiSellEntry>();
		
		public MultiSellListContainer()
		{
		}
		
		/**
		 * @param listId The listId to set.
		 */
		public void setListId(int listId)
		{
			_listId = listId;
		}
		
		public void setApplyTaxes(boolean applyTaxes)
		{
			_applyTaxes = applyTaxes;
		}
		
		public void setMaintainEnchantment(boolean maintainEnchantment)
		{
			_maintainEnchantment = maintainEnchantment;
		}
		
		public void addNpcId(int objId)
		{
			if (_npcIds == null)
			{
				synchronized (this)
				{
					if (_npcIds == null)
						_npcIds = new ArrayList<Integer>();
				}
			}
			
			_npcIds.add(objId);
		}
		
		/**
		 * @return Returns the listId.
		 */
		public int getListId()
		{
			return _listId;
		}
		
		public boolean getApplyTaxes()
		{
			return _applyTaxes;
		}
		
		public boolean getMaintainEnchantment()
		{
			return _maintainEnchantment;
		}
		
		public boolean checkNpcId(int npcId)
		{
			if (_npcIds == null)
				return false;
			
			return _npcIds.contains(npcId);
		}
		
		public void addEntry(MultiSellEntry e)
		{
			_entriesC.add(e);
		}
		
		public List<MultiSellEntry> getEntries()
		{
			return _entriesC;
		}
	}
	
	private void parse()
	{
		File[] files = Util.getDatapackFiles("multisell", ".xml");
		if (files.length == 0)
		{
			_log.info("World has no multisell lists");
			return;
		}
		
		for (File f : files)
		{
			try
			{
				int id = Integer.parseInt(f.getName().replaceAll(".xml", ""));
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setValidating(false);
				factory.setIgnoringComments(true);
				Document doc = factory.newDocumentBuilder().parse(f);
				
				MultiSellListContainer list = parseDocument(doc);
				list.setListId(id);
				_entries.put(id, list);
			}
			catch (Exception e)
			{
				_log.error("Error in file " + f.getAbsolutePath(), e);
			}
		}
	}
	
	private MultiSellListContainer parseDocument(Document doc)
	{
		MultiSellListContainer list = new MultiSellListContainer();
		
		int entryId = 1;
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				if (n.getAttributes() != null)
				{
					Node attribute;
					attribute = n.getAttributes().getNamedItem("applyTaxes");
					if (attribute == null)
						list.setApplyTaxes(false);
					else
						list.setApplyTaxes(Boolean.parseBoolean(attribute.getNodeValue()));
					attribute = n.getAttributes().getNamedItem("maintainEnchantment");
					if (attribute == null)
						list.setMaintainEnchantment(false);
					else
						list.setMaintainEnchantment(Boolean.parseBoolean(attribute.getNodeValue()));
				}
				
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("item".equalsIgnoreCase(d.getNodeName()))
					{
						MultiSellEntry e = parseEntry(d, entryId++);
						list.addEntry(e);
					}
				}
			}
		}
		
		return list;
	}
	
	private MultiSellEntry parseEntry(Node n, int entryId)
	{
		//int entryId = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
		
		Node first = n.getFirstChild();
		MultiSellEntry entry = new MultiSellEntry(entryId);
		
		for (n = first; n != null; n = n.getNextSibling())
		{
			if ("ingredient".equalsIgnoreCase(n.getNodeName()))
			{
				Node attribute;
				
				int id = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
				long count = Long.parseLong(n.getAttributes().getNamedItem("count").getNodeValue());
				
				boolean isTaxIngredient = false, maintainIngredient = false;
				
				attribute = n.getAttributes().getNamedItem("isTaxIngredient");
				
				if (attribute != null)
					isTaxIngredient = Boolean.parseBoolean(attribute.getNodeValue());
				
				attribute = n.getAttributes().getNamedItem("maintain");
				
				if (attribute != null)
					maintainIngredient = Boolean.parseBoolean(attribute.getNodeValue());
				
				MultiSellIngredient e = new MultiSellIngredient(id, count, isTaxIngredient, maintainIngredient);
				entry.addIngredient(e);
				
				validateItemId(id);
			}
			else if ("production".equalsIgnoreCase(n.getNodeName()))
			{
				int id = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
				long count = Long.parseLong(n.getAttributes().getNamedItem("count").getNodeValue());
				
				MultiSellIngredient e = new MultiSellIngredient(id, count);
				entry.addProduct(e);
				
				validateItemId(id);
			}
		}
		
		return entry;
	}
	
	private void validateItemId(int itemId)
	{
		switch (itemId)
		{
			case -200: // Clan Reputation Score
			case -300: // Player Fame
			{
				break;
			}
			default:
			{
				L2Item template = ItemTable.getInstance().getTemplate(itemId);
				if (template == null)
					_log.warn("[L2Multisell] can't find item with itemId: " + itemId, new IllegalStateException());
			}
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final MultisellTable _instance = new MultisellTable();
	}
}
