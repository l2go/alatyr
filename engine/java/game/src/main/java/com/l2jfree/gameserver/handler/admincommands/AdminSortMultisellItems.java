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
package com.l2jfree.gameserver.handler.admincommands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/*************************************/
/* command.equals("admin_sortmulti") */
/*************************************/

/**
 * 
 * Written by Darki699
 * This class handles following admin commands:
 * - //sortmulti = creates multisell lists in the /data/multisell/items folder (need to create the /items folder first)
 * @version $Revision: 1.2.4.4 $ $Date: 2007/07/31 10:06:02 $
 * 
 */

public class AdminSortMultisellItems implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_sortmulti" };
	
	private static final Logger _log = LoggerFactory.getLogger(AdminSortMultisellItems.class);
	
	private static boolean MULTISELL_GENERATE_OUTPUT_TEXT = true;
	private static boolean MULTISELL_GENERATE_UNKNOWN = true;
	private static boolean MULTISELL_BREAK_PROCESS = false;
	private static boolean MULTISELL_ID_SORT = false;
	private static boolean PROCESS_IS_RUNNING = false;
	private static boolean MULTISELL_GENERATE_SPECIAL_GEAR_LIST = false;
	private static boolean MULTISELL_GENERATE_JUNKLIST = false;
	private static boolean MULTISELL_GENERATE_SEEDLIST = false;
	private static boolean MULTISELL_GENERATE_MERCENARYLIST = false;
	private static boolean MULTISELL_GENERATE_HERBLIST = false;
	private static boolean MULTISELL_GENERATE_MONSTERONLY = false;
	private static boolean MULTISELL_GENERATE_FISHLIST = false;
	private static boolean MULTISELL_GENERATE_CLEAN_DIRECTORY = false;
	private static int DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS = 10;
	private static int DEFAULT_GM_SHOP_PRICE_MULTIPLIER = 2;
	
	private static final String path = Config.DATAPACK_ROOT.getAbsolutePath() + "/data/multisell/items/";
	
	String
	
	_weapon[] = { "NO_GRADE", "D_GRADE", "C_GRADE", "B_GRADE", "A_GRADE", "S_GRADE", "S80_GRADE", "Kamael", "SS" },
	
	_armor[] = { "NO_GRADE", "D_GRADE", "C_GRADE", "B_GRADE", "A_GRADE", "S_GRADE", "S80_GRADE", "Underwear" },
	
	_jewelry[] = { "NO_GRADE", "D_GRADE", "C_GRADE", "B_GRADE", "A_GRADE", "S_GRADE", "S80_GRADE" },
	
	_misc[] = { "Scroll", "Enchant", "L2Day", "Potions", "Hair", "Pet", "Quest", "EchoCrystal", "Recipe", "SpellBook",
			"Dyes" };
	
	String splitting = " abbdhtiowe ";
	
	/**
	 * Class holding the XML String for exach of the category items...
	 * @author Darki699
	 *
	 */
	private class GMObj
	{
		int count = 0;
		boolean typeSort = false;
		String type = null, xml = "<?xml version='1.0' encoding='utf-8'?>\n\n";
		Vector<String> itemNames = new Vector<String>();
		
		GMObj(String name, String parent)
		{
			type = name;
			xml += "<!-- " + parent + " : " + type + " -->\n\n<list>\n\n";
			
			if (parent.toLowerCase().equals("weapons"))
			{
				typeSort = true;
			}
		}
		
		void addItem(int itemId, int price)
		{
			String itemName = "";
			
			try
			{
				itemName = ItemTable.getInstance().getTemplate(itemId).getName();
			}
			catch (Exception e)
			{
				itemName = "unKnown";
			}
			
			String addString =
					(MULTISELL_ID_SORT) ? (itemId + splitting + itemName + splitting + price) : (itemName + splitting
							+ itemId + splitting + price);
			
			itemNames.add(addString);
			
			if (MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info(itemId + "(" + itemName + ") added to " + type);
			}
			
		}
		
		void generateXML()
		{
			String[] NameArray = itemNames.toArray(new String[itemNames.size()]);
			java.util.Arrays.sort(NameArray);
			if (typeSort)
			{
				NameArray = sortArrayByTypeAndPrice(NameArray);
			}
			
			for (String string : NameArray)
			{
				String param[] = string.split(splitting);
				
				if (param.length != 3)
				{
					continue;
				}
				
				String itemName = (MULTISELL_ID_SORT) ? param[1] : param[0];
				int price = 0;
				int itemId = 0;
				
				try
				{
					itemId = (param.length > 1) ? Integer.valueOf((MULTISELL_ID_SORT) ? param[0] : param[1]) : 0;
				}
				catch (Exception e)
				{
					itemId = 0;
				}
				
				try
				{
					price = (param.length > 2) ? Integer.valueOf(param[2]) : 0;
				}
				catch (Exception e)
				{
					price = 0;
				}
				
				count++;
				
				xml +=
						(price > 0) ? "  <!-- (" + itemName + ":Id " + itemId + ") for (" + price + " adena) --> \n"
								: "  <!-- (" + itemName + ":Id " + itemId + ") --> \n";
				// *DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS in order to leave spaces between the items...
				// Easier to modify later...
				xml += "  <item id=\"" + count * DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS + "\">\n";
				
				xml +=
						(price > 0) ? "    <ingredient id=\"57\" count=\"" + price + "\"/>\n"
								: "    <ingredient id=\"57\" count=\"?\"/>\n";
				xml += "    <production id=\"" + itemId + "\" count=\"1\"/>\n";
				xml += "  </item>\n\n";
				
			}
			
		}
		
		String getType()
		{
			return type.toLowerCase();
		}
		
		String getXML()
		{
			generateXML();
			return xml + "\n\n<!-- (" + count + " Items in this multisell) --> \n\n</list>\n";
		}
		
	}
	
	private Vector<GMObj> weapon, armor, jewelry, misc;
	
	private GMObj etc, etcQuest, etcEvent, etcSeed, etcHerb, etcMonster, etcMerc, etcFish, spellItems, infinity,
			unsealed_A_armor, unsealed_S_armor, SA_A_weapon, SA_S_weapon, SA_S80_weapon, unsealed_S80_armor;
	
	private List<Integer> spellItemList = null;
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_sortmulti"))
		{
			String param[] = command.split(" ");
			if (param.length == 1)
			{
				showHelp(activeChar);
				return false;
			}
			
			MULTISELL_BREAK_PROCESS = false;
			
			initSwitch();
			for (String opCommand : param)
			{
				opCommand = opCommand.toUpperCase();
				turnSwitch(opCommand);
			}
			
			if (PROCESS_IS_RUNNING)
			{
				activeChar.sendMessage("Process is already running.");
				activeChar.sendMessage("type: //sortmulti -b to break from this procedure.");
				return false;
			}
			
			PROCESS_IS_RUNNING = true;
			activeChar.sendMessage("Started to generate multisell lists:");
			activeChar.sendMessage(path);
			
			if (MULTISELL_BREAK_PROCESS)
			{
				activeChar.sendMessage("SortMultisell Process Terminated.");
				PROCESS_IS_RUNNING = false;
				return false;
			}
			
			doInit();
			init();
			makeMulti(activeChar);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void init()
	{
		for (String s : _weapon)
		{
			weapon.add(new GMObj(s, "Weapons"));
		}
		for (String s : _armor)
		{
			armor.add(new GMObj(s, "Armors"));
		}
		for (String s : _jewelry)
		{
			jewelry.add(new GMObj(s, "Jewelry"));
		}
		for (String s : _misc)
		{
			misc.add(new GMObj(s, "Miscellaneous"));
		}
	}
	
	private void makeMulti(L2Player actor)
	{
		String[] SQL_ITEM_SELECTS =
				{ "SELECT id, price FROM etc_item_template", "SELECT id, price FROM armor_template",
						"SELECT id, price FROM weapon_template" };
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			for (String selectQuery : SQL_ITEM_SELECTS)
			{
				if (MULTISELL_BREAK_PROCESS)
				{
					break;
				}
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				
				if (MULTISELL_GENERATE_OUTPUT_TEXT)
				{
					_log.info("!!!!!!!!!!!!" + selectQuery + "!!!!!!!!!!!!");
				}
				
				while (rset.next())
				{
					if (MULTISELL_BREAK_PROCESS)
					{
						break;
					}
					
					Integer i = rset.getInt("id");
					int itemId = i;
					
					int price = (int)rset.getLong("price");
					
					price = getGMShopPrice(itemId, price);
					
					if (isSpellItem(itemId))
					{
						spellItems.addItem(itemId, price);
					}
					else if (price <= 0)
					{
						addEtc(itemId, 0);
					}
					else if (selectQuery.endsWith("armor_template"))
					{
						addArmor(itemId, price);
					}
					else if (selectQuery.endsWith("weapon_template"))
					{
						addWeapon(itemId, price);
					}
					else if (selectQuery.endsWith("etc_item_template"))
					{
						addMisc(itemId, price);
					}
					
				}
				rset.close();
				statement.close();
				
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		if (MULTISELL_BREAK_PROCESS)
		{
			PROCESS_IS_RUNNING = false;
			actor.sendMessage("SortMultisell Process Terminated.");
			if (MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info("SortMultisell Process was Terminated by an Administrator... HALT was initiated!");
			}
			doInit();
			return;
		}
		
		writeXML();
	}
	
	private int getGMShopPrice(int itemId, int price)
	{
		int priceGm = price;
		Connection con = null;
		PreparedStatement statement = null;
		
		// A good without a price has the reference price of the item, so it is no GM price
		String SELECT_LIST[] =
				{ "SELECT item_template_id, price FROM custom_merchant_buylist WHERE price IS NOT NULL" };
		
		for (String QUERY : SELECT_LIST)
		{
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				statement = con.prepareStatement(QUERY);
				ResultSet rset = statement.executeQuery();
				while (rset.next())
				{
					Integer i = rset.getInt("item_template_id");
					long p = rset.getLong("price");
					if (itemId == i && p > priceGm)
					{
						priceGm = (int)p;
					}
				}
				rset.close();
				statement.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
		return ((priceGm == price) ? (price * DEFAULT_GM_SHOP_PRICE_MULTIPLIER) : priceGm);
	}
	
	private GMObj getXML(Vector<GMObj> vector, String name)
	{
		name = name.toLowerCase();
		for (GMObj o : vector)
			if (o.getType().equals(name))
				return o;
		return null;
	}
	
	private void addXML(GMObj category, int itemId, int price)
	{
		if (category == null)
		{
			addEtc(itemId, price);
		}
		else
		{
			category.addItem(itemId, price);
		}
		
	}
	
	private void addJewelry(L2Item item, int price)
	{
		final String[] forbidden = { "valakas", "antharas", "core", "baium", "queen ant", "zaken", "orfen" };
		GMObj category = null;
		String itemName = item.getName();
		
		for (String s : forbidden)
		{
			if (itemName.toLowerCase().contains(s))
			{
				if (MULTISELL_GENERATE_JUNKLIST)
					etc.addItem(item.getItemId(), price);
				return;
			}
		}
		
		switch (item.getCrystalGrade())
		{
			case L2Item.CRYSTAL_S:
				if (!itemName.toLowerCase().contains("seal") && !item.isStackable())
				{
					if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
					{
						if (itemName.toLowerCase().contains("dynasty"))
						{
							unsealed_S80_armor.addItem(item.getItemId(), price);
							return;
						}
						
						unsealed_S_armor.addItem(item.getItemId(), price);
						return;
					}
					
					else if (MULTISELL_GENERATE_JUNKLIST)
						etc.addItem(item.getItemId(), price);
					
					return;
				}
				if (itemName.toLowerCase().contains("dynasty"))
				{
					category = getXML(jewelry, "S80_GRADE");
					addXML(category, item.getItemId(), price);
					return;
				}
				category = getXML(jewelry, "S_GRADE");
				addXML(category, item.getItemId(), price);
				return;
			case L2Item.CRYSTAL_A:
				if (!itemName.toLowerCase().contains("seal") && !item.isStackable())
				{
					if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
					{
						unsealed_A_armor.addItem(item.getItemId(), price);
						return;
					}
					
					else if (MULTISELL_GENERATE_JUNKLIST)
						etc.addItem(item.getItemId(), price);
					
					return;
				}
				category = getXML(jewelry, "A_GRADE");
				addXML(category, item.getItemId(), price);
				return;
			case L2Item.CRYSTAL_B:
				category = getXML(jewelry, "B_GRADE");
				addXML(category, item.getItemId(), price);
				return;
			case L2Item.CRYSTAL_C:
				category = getXML(jewelry, "C_GRADE");
				addXML(category, item.getItemId(), price);
				return;
			case L2Item.CRYSTAL_D:
				category = getXML(jewelry, "D_GRADE");
				addXML(category, item.getItemId(), price);
				return;
			case L2Item.CRYSTAL_NONE:
				if (itemName.toLowerCase().contains("dynasty"))
				{
					if (!itemName.toLowerCase().contains("seal") && !item.isStackable())
					{
						if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
						{
							unsealed_S80_armor.addItem(item.getItemId(), price);
							return;
						}
						
						else if (MULTISELL_GENERATE_JUNKLIST)
							etc.addItem(item.getItemId(), price);
						return;
					}
					category = getXML(jewelry, "S80_GRADE");
					addXML(category, item.getItemId(), price);
					return;
				}
				
				category = getXML(jewelry, "NO_GRADE");
				addXML(category, item.getItemId(), price);
				return;
		}
		
		if (MULTISELL_GENERATE_JUNKLIST)
			etc.addItem(item.getItemId(), price);
	}
	
	private void addArmor(int itemId, int price)
	{
		//"NO_GRADE" , "D_GRADE" , "C_GRADE" , "B_GRADE" , "A_GRADE" , "S_GRADE" , "S80_GRADE" , "Kamael" , "Underwear"
		L2Item item = null;
		GMObj category = null;
		
		try
		{
			item = ItemTable.getInstance().getTemplate(itemId);
		}
		catch (Exception e)
		{
			addEtc(itemId, price);
			return;
		}
		if (item == null)
		{
			addEtc(itemId, price);
			return;
		}
		
		switch (item.getBodyPart())
		{
			case L2Item.SLOT_UNDERWEAR:
				category = getXML(armor, "Underwear");
				addXML(category, itemId, price);
				return;
			case L2Item.SLOT_HAIR:
			case L2Item.SLOT_HAIR2:
			case L2Item.SLOT_HAIRALL:
				category = getXML(misc, "Hair");
				addXML(category, itemId, price);
				return;
			case L2Item.SLOT_L_EAR:
			case L2Item.SLOT_R_EAR:
			case L2Item.SLOT_LR_EAR:
			case L2Item.SLOT_L_FINGER:
			case L2Item.SLOT_R_FINGER:
			case L2Item.SLOT_LR_FINGER:
			case L2Item.SLOT_NECK:
				addJewelry(item, price);
				return;
			case L2Item.SLOT_BABYPET:
			case L2Item.SLOT_HATCHLING:
			case L2Item.SLOT_STRIDER:
			case L2Item.SLOT_WOLF:
				category = getXML(misc, "Pet");
				addXML(category, itemId, price);
				return;
		}
		
		if (item.isForWolf() || item.isForHatchling() || item.isForStrider() || item.isForBabyPet())
		{
			category = getXML(misc, "Pet");
			addXML(category, itemId, price);
			return;
		}
		
		switch (item.getCrystalGrade())
		{
			case L2Item.CRYSTAL_S:
				if (!item.getName().toLowerCase().contains("seal") && !item.isStackable())
				{
					if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
					{
						if (item.getName().toLowerCase().contains("dynasty"))
						{
							unsealed_S80_armor.addItem(item.getItemId(), price);
							return;
						}
						
						unsealed_S_armor.addItem(item.getItemId(), price);
						return;
					}
					
					else if (MULTISELL_GENERATE_JUNKLIST)
						etc.addItem(item.getItemId(), price);
					return;
				}
				if (item.getName().toLowerCase().contains("dynasty"))
				{
					category = getXML(armor, "S80_GRADE");
					addXML(category, itemId, price);
					return;
				}
				category = getXML(armor, "S_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_A:
				if (!item.getName().toLowerCase().contains("seal") && !item.isStackable())
				{
					if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
					{
						unsealed_A_armor.addItem(item.getItemId(), price);
						return;
					}
					
					else if (MULTISELL_GENERATE_JUNKLIST)
						etc.addItem(item.getItemId(), price);
					return;
				}
				category = getXML(armor, "A_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_B:
				category = getXML(armor, "B_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_C:
				category = getXML(armor, "C_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_D:
				category = getXML(armor, "D_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_NONE:
				if (item.getName().toLowerCase().contains("dynasty"))
				{
					if (!item.getName().toLowerCase().contains("seal") && !item.isStackable())
					{
						if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
						{
							unsealed_S80_armor.addItem(item.getItemId(), price);
							return;
						}
						
						if (MULTISELL_GENERATE_JUNKLIST)
							etc.addItem(item.getItemId(), price);
						return;
					}
					category = getXML(armor, "S80_GRADE");
					addXML(category, itemId, price);
					return;
				}
				
				category = getXML(armor, "NO_GRADE");
				addXML(category, itemId, price);
				return;
		}
		if (MULTISELL_GENERATE_JUNKLIST)
			etc.addItem(item.getItemId(), price);
	}
	
	private void addWeapon(int itemId, int price)
	{
		//"NO_GRADE" , "D_GRADE" , "C_GRADE" , "B_GRADE" , "A_GRADE" , "S_GRADE" , "S80_GRADE" , "Kamael" , "SS"
		L2Item item = null;
		GMObj category = null;
		
		try
		{
			item = ItemTable.getInstance().getTemplate(itemId);
		}
		catch (Exception e)
		{
			addEtc(itemId, price);
			return;
		}
		if (item == null)
		{
			addEtc(itemId, price);
			return;
		}
		else if (item.getName().toLowerCase().contains("monster only"))
		{
			//May add a monster-only list
			if (MULTISELL_GENERATE_MONSTERONLY)
				etcMonster.addItem(itemId, price);
			return;
		}
		
		switch (item.getBodyPart())
		{
			case L2Item.SLOT_L_HAND:
				if (!item.isStackable())
				{
					addArmor(itemId, price);
					return;
				}
		}
		
		if (item.getItemType() == L2EtcItemType.SHOT)
		{
			category = getXML(weapon, "SS");
			addXML(category, itemId, price);
			return;
		}
		
		if (item.getItemType() == L2WeaponType.CROSSBOW || item.getItemType() == L2WeaponType.ANCIENT_SWORD
				|| item.getItemType() == L2WeaponType.RAPIER || item.getItemType() == L2EtcItemType.BOLT)
		{
			if (item.getName().toLowerCase().contains(" - "))
			{
				if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
				{
					if (item.getName().toLowerCase().contains("dynasty"))
					{
						SA_S80_weapon.addItem(itemId, price);
						return;
					}
					
					switch (item.getCrystalGrade())
					{
						case L2Item.CRYSTAL_S:
							SA_S_weapon.addItem(itemId, price);
							return;
						case L2Item.CRYSTAL_A:
							SA_A_weapon.addItem(itemId, price);
							return;
					}
				}
				else if (MULTISELL_GENERATE_JUNKLIST)
					etc.addItem(item.getItemId(), price);
				
				return;
			}
			category = getXML(weapon, "Kamael");
			addXML(category, itemId, price);
			return;
		}
		
		if (item.getName().toLowerCase().contains(" - "))
		{
			if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
			{
				if (item.getName().toLowerCase().contains("dynasty"))
				{
					SA_S80_weapon.addItem(itemId, price);
					return;
				}
				switch (item.getCrystalGrade())
				{
					case L2Item.CRYSTAL_S:
						SA_S_weapon.addItem(itemId, price);
						return;
					case L2Item.CRYSTAL_A:
						SA_A_weapon.addItem(itemId, price);
						return;
				}
			}
			
			else if (MULTISELL_GENERATE_JUNKLIST)
				etc.addItem(item.getItemId(), price);
			return;
		}
		
		switch (item.getCrystalGrade())
		{
			case L2Item.CRYSTAL_S:
				if (item.getName().toLowerCase().contains("infinity"))
				{
					if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
					{
						infinity.addItem(item.getItemId(), price);
					}
					else if (MULTISELL_GENERATE_JUNKLIST)
						etc.addItem(item.getItemId(), price);
					return;
				}
				else if (item.getName().toLowerCase().contains("dynasty"))
				{
					category = getXML(weapon, "S80_GRADE");
					addXML(category, itemId, price);
					return;
				}
				category = getXML(weapon, "S_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_A:
				category = getXML(weapon, "A_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_B:
				category = getXML(weapon, "B_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_C:
				category = getXML(weapon, "C_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_D:
				category = getXML(weapon, "D_GRADE");
				addXML(category, itemId, price);
				return;
			case L2Item.CRYSTAL_NONE:
				if (item.getName().toLowerCase().contains("dynasty"))
				{
					category = getXML(weapon, "S80_GRADE");
					addXML(category, itemId, price);
					return;
				}
				
				category = getXML(weapon, "NO_GRADE");
				addXML(category, itemId, price);
				return;
		}
		if (MULTISELL_GENERATE_JUNKLIST)
			etc.addItem(item.getItemId(), price);
	}
	
	private void addMisc(int itemId, int price)
	{
		//"Scroll" , "Enchant" , "L2Day" , "Potions" , "Hair" , "Pet" , "Quest" , "EchoCrystal" , "Recipe" , "SpellBooks" , "Dyes"
		L2Item item = null;
		GMObj category = null;
		
		try
		{
			item = ItemTable.getInstance().getTemplate(itemId);
		}
		catch (Exception e)
		{
			addEtc(itemId, price);
			return;
		}
		
		if (item == null)
		{
			addEtc(itemId, price);
		}
		
		else if (item.getName().toLowerCase().contains("echo crystal"))
		{
			category = getXML(misc, "EchoCrystal");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.RECEIPE)
		{
			category = getXML(misc, "Recipe");
			addXML(category, itemId, price);
		}
		
		else if (item.getName().contains("+") && item.getName().contains("-")
				&& item.getName().toLowerCase().contains("dye"))
		{
			category = getXML(misc, "Dyes");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.SPELLBOOK)
		{
			category = getXML(misc, "SpellBook");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.SCROLL)
		{
			if (item.getName().toLowerCase().contains("bless") || item.getName().toLowerCase().contains("mercenary"))
			{
				if (MULTISELL_GENERATE_MERCENARYLIST && item.getName().toLowerCase().contains("mercenary"))
					etcMerc.addItem(itemId, price);
				
				if (MULTISELL_GENERATE_JUNKLIST && item.getName().toLowerCase().contains("bless"))
					etc.addItem(itemId, price);
				return;
			}
			
			if (item.getName().toLowerCase().contains("l2day"))
			{
				category = getXML(misc, "L2Day");
				addXML(category, itemId, price);
				return;
			}
			else if (item.getName().toLowerCase().contains("enchant"))
			{
				category = getXML(misc, "Enchant");
				addXML(category, itemId, price);
				return;
			}
			
			category = getXML(misc, "Scroll");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.PET_COLLAR)
		{
			category = getXML(misc, "Pet");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.POTION)
		{
			category = getXML(misc, "Potions");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.QUEST)
		{
			category = getXML(misc, "Quest");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.SHOT)
		{
			category = getXML(weapon, "SS");
			addXML(category, itemId, price);
		}
		
		else if (item.getItemType() == L2EtcItemType.ARROW || item.getItemType() == L2EtcItemType.BOLT)
		{
			addWeapon(itemId, price);
		}
		
		//Hair taken care of in addArmor.
		else
		{
			if (MULTISELL_GENERATE_JUNKLIST)
				etc.addItem(itemId, price);
		}
	}
	
	static private boolean deleteDirectory(File directoryPath)
	{
		if (MULTISELL_GENERATE_OUTPUT_TEXT)
		{
			_log.info("Deleting directory " + directoryPath.getName());
		}
		
		if (directoryPath.exists())
		{
			
			for (File file : directoryPath.listFiles())
			{
				if (file.isDirectory())
				{
					deleteDirectory(file);
				}
				else
				{
					if (MULTISELL_GENERATE_OUTPUT_TEXT)
					{
						_log.info("Deleting File: " + file.getName());
					}
					
					file.delete();
				}
			}
		}
		
		return (directoryPath.delete());
	}
	
	private void writeXML()
	{
		boolean successValue = true;
		final String explanation =
				"This file is a UNKNOWN ITEMS dump...\n" + "Keep these items under watch because they\n"
						+ "may be changed. Right now they are unknown,\n"
						+ "or have errors, or have a ZERO OR LESS PRICE\n"
						+ "value so they're not fit to be in a regular \n"
						+ "GM Shop. Maybe they will change, maybe you decide\n"
						+ "to modify them... For these reason they are saved.\n";
		
		if (MULTISELL_GENERATE_CLEAN_DIRECTORY)
		{
			if (deleteDirectory(new File(path)) && MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info("Cleaned destination directory " + path);
			}
			
			if ((new File(path)).mkdir() && MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info("Directory: " + path + " created");
			}
			
		}
		
		for (GMObj o : armor)
		{
			if (!writeFile(o, "Armor_", ""))
				successValue = false;
		}
		for (GMObj o : weapon)
		{
			if (!writeFile(o, "Weapon_", ""))
				successValue = false;
		}
		for (GMObj o : jewelry)
		{
			if (!writeFile(o, "Jewelry_", ""))
				successValue = false;
		}
		for (GMObj o : misc)
		{
			if (!writeFile(o, "Misc_", ""))
				successValue = false;
		}
		
		if (!writeFile(spellItems, "Casting_", ""))
			successValue = false;
		
		if (MULTISELL_GENERATE_SPECIAL_GEAR_LIST)
		{
			if (!writeFile(infinity, "Special_", ""))
				successValue = false;
			if (!writeFile(SA_A_weapon, "Special_", ""))
				successValue = false;
			if (!writeFile(SA_S_weapon, "Special_", ""))
				successValue = false;
			if (!writeFile(SA_S80_weapon, "Special_", ""))
				successValue = false;
			if (!writeFile(unsealed_A_armor, "Special_", ""))
				successValue = false;
			if (!writeFile(unsealed_S_armor, "Special_", ""))
				successValue = false;
			if (!writeFile(unsealed_S80_armor, "Special_", ""))
				successValue = false;
		}
		
		if (MULTISELL_GENERATE_UNKNOWN)
		{
			if (!writeFile(etc, "UNKNOWN_", explanation))
				successValue = false;
			if (!writeFile(etcQuest, "UNKNOWN_", explanation))
				successValue = false;
			if (!writeFile(etcEvent, "UNKNOWN_", explanation))
				successValue = false;
			
			if (MULTISELL_GENERATE_MONSTERONLY)
				if (!writeFile(etcMonster, "UNKNOWN_", explanation))
					successValue = false;
			if (MULTISELL_GENERATE_SEEDLIST)
				if (!writeFile(etcSeed, "UNKNOWN_", explanation))
					successValue = false;
			if (MULTISELL_GENERATE_FISHLIST)
				if (!writeFile(etcFish, "UNKNOWN_", explanation))
					successValue = false;
			if (MULTISELL_GENERATE_MERCENARYLIST)
				if (!writeFile(etcMerc, "UNKNOWN_", explanation))
					successValue = false;
			if (MULTISELL_GENERATE_HERBLIST)
				if (!writeFile(etcHerb, "UNKNOWN_", explanation))
					successValue = false;
		}
		
		if (MULTISELL_GENERATE_OUTPUT_TEXT)
		{
			if (successValue)
			{
				_log.info("Finished Multisell Processing Files. Operation was Successful.");
				_log.info("Check: " + path + " for your new XML files.");
				_log.info("sortmulti: SUCCESS!");
			}
			else
			{
				_log.info("One or more files were not generated successfully.");
				_log.info("Try using //sortmulti -clean command in order to clean the directory and overwrite the existing files.");
				_log.info("sortmulti: FAILURE!");
			}
		}
		
		PROCESS_IS_RUNNING = false;
	}
	
	private boolean writeFile(GMObj xml, String parent, String explanation)
	{
		String fileName = path + parent + xml.getType() + ".xml";
		File file;
		file = new File(fileName);
		if (!file.exists())
		{
			try
			{
				file.createNewFile();
				if (MULTISELL_GENERATE_OUTPUT_TEXT)
				{
					_log.info(fileName + " is being created...");
				}
			}
			catch (Exception e)
			{
				if (MULTISELL_GENERATE_OUTPUT_TEXT)
				{
					_log.info(fileName + "!");
					_log.info("Error creating file! ");
				}
				return false;
			}
		}
		else
		{
			if (MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info(fileName);
				_log.info("Already exist, will not replace it!");
			}
			return false;
		}
		
		try
		{
			FileWriter fstream = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(fstream);
			
			if (!explanation.isEmpty())
				out.write("\n" + explanation + "\n\n");
			
			out.write(xml.getXML());
			//Close the output stream
			out.close();
		}
		catch (Exception e)
		{
			if (MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				_log.info("Error writing to file: " + fileName + "!");
			}
			return false;
		}
		return true;
	}
	
	private void addEtc(int itemId, int price)
	{
		if (!MULTISELL_GENERATE_UNKNOWN)
		{
			return;
		}
		
		try
		{
			L2Item item = ItemTable.getInstance().getTemplate(itemId);
			if (item == null)
			{
				etc.addItem(itemId, price);
			}
			else if (item.getItemType() == L2EtcItemType.QUEST)
			{
				etcQuest.addItem(itemId, price);
			}
			else if (item.getName().toLowerCase().contains("event"))
			{
				etcEvent.addItem(itemId, price);
			}
			else if (item.getName().toLowerCase().contains("mercenary")
					|| item.getName().toLowerCase().contains("posting ticket"))
			{
				if (MULTISELL_GENERATE_MERCENARYLIST)
					etcMerc.addItem(itemId, price);
			}
			else if (item.getItemType() == L2EtcItemType.HERB)
			{
				//May add a Herb list
				if (MULTISELL_GENERATE_HERBLIST)
					etcHerb.addItem(itemId, price);
			}
			else if (item.getName().toLowerCase().contains("fish"))
			{
				//May add a fish list
				if (MULTISELL_GENERATE_FISHLIST)
					etcFish.addItem(itemId, price);
			}
			else if (item.getName().toLowerCase().contains("seed"))
			{
				//May add a seed list
				if (MULTISELL_GENERATE_SEEDLIST)
					etcSeed.addItem(itemId, price);
			}
			else if (item.getName().toLowerCase().contains("monster only"))
			{
				//May add a monster-only list
				if (MULTISELL_GENERATE_MONSTERONLY)
					etcMonster.addItem(itemId, price);
			}
			else
			{
				etc.addItem(itemId, price);
			}
		}
		catch (Exception e)
		{
			etc.addItem(itemId, price);
		}
		
	}
	
	private void showHelp(L2Player activeChar)
	{
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		StringBuilder replyMSG = new StringBuilder("<html><title>Multisells from ItemTable</title><body>");
		
		replyMSG.append("<center><font color=\"LEVEL\">[Multisell Engine by Darki699]</font></center><br><br>");
		replyMSG.append("<center>//sortmulti <opCommand 1> <opCommand 2> ...</center><br>");
		replyMSG.append("<table><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"LEVEL\"> CMD </font></td><td width=\"140\"><font color=\"LEVEL\">Description</font></td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -OUT </font></td><td width=\"140\"> Cancels the output text debug of progress. Default: Show output.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -GEAR </font></td><td width=\"140\"> Adds XMLs for SA A,S Grade weapons, List for Infinity, Unsealed Armors S,A Grades. Default: Do not do that.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -IDSORT </font></td><td width=\"140\"> Sort items in files by IDs. Default: Sort by Item Name.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -CLEAN </font></td><td width=\"140\"> Overwrites existing /items files in the directory. Deletes directory and builds it again. Default: Do not overwrite.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -RUN | -R </font></td><td width=\"140\"> Runs the generator. </td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -JUNK </font></td><td width=\"140\"> Adds junk items to UnKnown.xml. Default: Discard unknown junk. </td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -UNK </font></td><td width=\"140\"> Cancels UnKnown items XML file generation. Default: Generate unknown items file.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -B | -BREAK </font></td><td width=\"140\"> Breaks the process in the middle. </td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -SEED </font></td><td width=\"140\"> Generate a Seed list. Default: Do not generate this.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -HERB </font></td><td width=\"140\"> Generate a Herb list. Default: Do not generate this.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -MERC </font></td><td width=\"140\"> Generate a Mercenary list. Default: Do not generate this.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -FISH </font></td><td width=\"140\"> Generate a Fish list. Default: Do not generate this.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -MON </font></td><td width=\"140\"> Generate a Monster Only Items list. Default: Do not generate this.</td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -PRICE:</font><br1>number </td><td width=\"140\"> xMultiplier price settings for items with a default shop price only: Default x2. </td></tr><tr>");
		replyMSG.append("<td width=\"140\"><font color=\"00FF00\"> -JUMP:</font><br1>number </td><td width=\"140\"> Generate list with Item Jump [number]. Default:10. </td></tr><tr>");
		replyMSG.append("</tr></table><br>...Example:<br><font color=\"FF0000\">//sortmulti -out -seed -monster -price:5 -jump:3</font><br>");
		replyMSG.append("...This will generate without output, only files. Will additionally");
		replyMSG.append(" generate a Seed.XML and MonsterOnly.XML files. All prices if not");
		replyMSG.append(" defined in custom_merchant_buylist.sql, will be x5 more expensive. Items in the");
		replyMSG.append(" XML files will be sorted with 3 number spaces difference, jumping from 1->4->7...");
		replyMSG.append("</body></html>");
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
		
	}
	
	private void initSwitch()
	{
		MULTISELL_GENERATE_OUTPUT_TEXT = true;
		MULTISELL_GENERATE_UNKNOWN = true;
		MULTISELL_GENERATE_JUNKLIST = false;
		MULTISELL_GENERATE_SEEDLIST = false;
		MULTISELL_GENERATE_MERCENARYLIST = false;
		MULTISELL_GENERATE_HERBLIST = false;
		MULTISELL_GENERATE_MONSTERONLY = false;
		MULTISELL_GENERATE_FISHLIST = false;
		MULTISELL_ID_SORT = false;
		MULTISELL_GENERATE_CLEAN_DIRECTORY = false;
		MULTISELL_GENERATE_SPECIAL_GEAR_LIST = false;
		DEFAULT_GM_SHOP_PRICE_MULTIPLIER = 2;
		DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS = 10;
	}
	
	private void turnSwitch(String cmd)
	{
		
		if (cmd.equals("-OUT"))
		{
			MULTISELL_GENERATE_OUTPUT_TEXT = false;
		}
		
		else if (cmd.equals("-IDSORT"))
		{
			MULTISELL_ID_SORT = true;
		}
		
		else if (cmd.equals("-CLEAN"))
		{
			MULTISELL_GENERATE_CLEAN_DIRECTORY = true;
		}
		
		else if (cmd.equals("-RUN") || cmd.equals("-R"))
		{
			//do nothing - Runs the XML Generating Process if no opCommand is found.
		}
		
		else if (cmd.equals("-GEAR"))
		{
			MULTISELL_GENERATE_SPECIAL_GEAR_LIST = true;
		}
		
		else if (cmd.equals("-JUNK"))
		{
			MULTISELL_GENERATE_JUNKLIST = true;
		}
		
		else if (cmd.equals("-UNK"))
		{
			MULTISELL_GENERATE_UNKNOWN = false;
		}
		
		else if (cmd.equals("-B") || cmd.equals("-BREAK"))
		{
			MULTISELL_BREAK_PROCESS = true;
		}
		
		else if (cmd.equals("-SEED"))
		{
			MULTISELL_GENERATE_SEEDLIST = true;
		}
		
		else if (cmd.equals("-HERB"))
		{
			MULTISELL_GENERATE_HERBLIST = true;
		}
		
		else if (cmd.equals("-MERC"))
		{
			MULTISELL_GENERATE_MERCENARYLIST = true;
		}
		
		else if (cmd.equals("-MON"))
		{
			MULTISELL_GENERATE_MONSTERONLY = true;
		}
		
		else if (cmd.equals("-FISH"))
		{
			MULTISELL_GENERATE_FISHLIST = true;
		}
		
		else if (cmd.startsWith("-PRICE:"))
		{
			try
			{
				DEFAULT_GM_SHOP_PRICE_MULTIPLIER = Integer.valueOf(cmd.substring(7));
			}
			catch (Exception e)
			{
				DEFAULT_GM_SHOP_PRICE_MULTIPLIER = 2;
			}
		}
		
		else if (cmd.startsWith("-JUMP:"))
		{
			try
			{
				DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS = Integer.valueOf(cmd.substring(6));
			}
			catch (Exception e)
			{
				DEFAULT_SPACES_BETWEEN_MULTISELL_LIST_ITEMS = 10;
			}
		}
	}
	
	private void doInit()
	{
		weapon = new Vector<GMObj>();
		armor = new Vector<GMObj>();
		jewelry = new Vector<GMObj>();
		misc = new Vector<GMObj>();
		
		etc = new GMObj("Unknown", "Other");
		etcQuest = new GMObj("Quest_Items", "No Price");
		etcEvent = new GMObj("Event_Items", "Other");
		
		etcSeed = new GMObj("Seed", "Manor and Seeds");
		etcMerc = new GMObj("Mercenary", "Posting Ticket");
		etcFish = new GMObj("Fish", "Fishing items");
		etcHerb = new GMObj("Herb", "Herb");
		etcMonster = new GMObj("MonsterOnly", "Monster Only Items");
		spellItems = new GMObj("Spell_Items", "Casting");
		
		infinity = new GMObj("Infinity_Weapons", "Hero Weapons");
		SA_A_weapon = new GMObj("S_Grade_Weapons_with_SA", "S Grade Weapons");
		SA_S_weapon = new GMObj("A_Grade_Weapons_with_SA", "A Grade Weapons");
		SA_S80_weapon = new GMObj("S80_Grade_Weapons_with_SA", "S80 Grade Weapons");
		unsealed_A_armor = new GMObj("A_Grade_Unsealed_Armors", "A Grade Armors");
		unsealed_S_armor = new GMObj("S_Grade_Unsealed_Armors", "S Grade Armors");
		unsealed_S80_armor = new GMObj("S80_Grade_Unsealed_Armors", "S80 Grade Armors");
		
		MULTISELL_BREAK_PROCESS = false;
	}
	
	private boolean isSpellItem(int itemId)
	{
		if (spellItemList == null)
		{
			spellItemList = new ArrayList<Integer>();
			
			for (ClassId classId : ClassId.values())
			{
				try
				{
					
					for (com.l2jfree.gameserver.model.skills.learn.L2SkillLearn skill : SkillTreeTable.getInstance()
							.getAllowedSkills(classId))
					{
						try
						{
							
							L2Skill s = SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel());
							if (s.getItemConsumeId() == 0)
								continue;
							Integer spellItemId = s.getItemConsumeId();
							if (!spellItemList.contains(spellItemId))
							{
								spellItemList.add(spellItemId);
							}
							
						}
						catch (Exception e)
						{
						}
					}
					
				}
				catch (Exception e)
				{
					continue;
				}
			}
			if (MULTISELL_GENERATE_OUTPUT_TEXT)
			{
				for (Integer item : spellItemList)
				{
					_log.info("Item used to cast a spell: " + item);
				}
			}
		}
		
		return (spellItemList.contains(Integer.valueOf(itemId)));
	}
	
	private String[] sortArrayByTypeAndPrice(String[] array)
	{
		Vector<L2Item> items = getItemVector(array);
		Vector<String> newArray = new Vector<String>();
		items = sortItemsByPrice(items, array);
		items = sortItemsByType(items);
		
		for (L2Item item : items)
		{
			if (item == null)
				continue;
			
			int itemId = item.getItemId();
			for (String finalElement : array)
			{
				String[] param = finalElement.split(splitting);
				
				if (param.length != 3)
					continue;
				
				String temp = (MULTISELL_ID_SORT) ? param[0] : param[1];
				int id = Integer.valueOf(temp);
				
				if (itemId == id)
				{
					newArray.add(finalElement);
				}
			}
		}
		
		return newArray.toArray(new String[newArray.size()]);
	}
	
	private Vector<L2Item> getItemVector(String[] array)
	{
		Vector<L2Item> items = new Vector<L2Item>();
		
		for (String string : array)
		{
			String param[] = string.split(splitting);
			
			if (param.length != 3)
				continue;
			
			String itemID = (MULTISELL_ID_SORT) ? param[0] : param[1];
			
			try
			{
				L2Item item = ItemTable.getInstance().getTemplate(Integer.valueOf(itemID));
				
				if (item == null)
					continue;
				
				items.add(item);
			}
			catch (Exception e)
			{
				continue;
			}
			
		}
		return items;
	}
	
	private Vector<L2Item> sortItemsByPrice(Vector<L2Item> items, String[] array)
	{
		
		Vector<Integer> itemPrice = new Vector<Integer>();
		
		for (String string : array)
		{
			String[] param = string.split(splitting);
			
			if (param.length != 3)
				continue;
			
			String itemID = (MULTISELL_ID_SORT) ? param[0] : param[1];
			
			try
			{
				L2Item item = ItemTable.getInstance().getTemplate(Integer.valueOf(itemID));
				Integer price = Integer.valueOf(param[2]);
				
				if (item == null)
					continue;
				
				itemPrice.add(price);
			}
			
			catch (Exception e)
			{
				continue;
			}
		}
		
		for (int x = 0; x < itemPrice.size() - 1; x++)
			for (int y = x + 1; y < itemPrice.size(); y++)
			{
				if (itemPrice.get(x) > itemPrice.get(y))
				{
					try
					{
						//swap integers
						Integer temp = itemPrice.get(x);
						itemPrice.set(x, itemPrice.get(y));
						itemPrice.set(y, temp);
						//swap items
						L2Item tempItem = items.get(x);
						items.set(x, items.get(y));
						items.set(y, tempItem);
					}
					catch (Exception e)
					{
						continue;
					}
				}
			}
		
		return items;
	}
	
	private Vector<L2Item> sortItemsByType(Vector<L2Item> items)
	{
		
		Vector<L2Item> newList = new Vector<L2Item>();
		for (L2WeaponType type : L2WeaponType.values())
		{
			
			if (type == null)
				continue;
			
			for (L2Item item : items)
			{
				if (item == null)
					continue;
				
				else if (!(item instanceof com.l2jfree.gameserver.model.items.templates.L2Weapon))
					continue;
				
				L2WeaponType weaponType = ((com.l2jfree.gameserver.model.items.templates.L2Weapon)item).getItemType();
				
				try
				{
					if (weaponType == type && !newList.contains(item))
					{
						newList.add(item);
					}
					
				}
				catch (Exception e)
				{
					continue;
				}
			}
		}
		
		for (L2Item item : items)
		{
			if (!newList.contains(item))
				newList.add(item);
		}
		
		return newList;
	}
}
