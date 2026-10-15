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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.document.DocumentEngine;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.L2CommandChannel;
import com.l2jfree.gameserver.model.items.Item;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance.ItemLocation;
import com.l2jfree.gameserver.model.items.templates.L2ArmorType;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.persistence.item.ItemRepository;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.util.L2Collections;

public final class ItemTable
{
	private static final Logger _log = LoggerFactory.getLogger(ItemTable.class);
	private static final Logger _logItems = LoggerFactory.getLogger("item");
	
	private static final Map<String, Integer> _materials = new HashMap<String, Integer>();
	private static final Map<String, Integer> _crystalTypes = new HashMap<String, Integer>();
	private static final Map<String, L2WeaponType> _weaponTypes = new HashMap<String, L2WeaponType>();
	private static final Map<String, L2ArmorType> _armorTypes = new HashMap<String, L2ArmorType>();
	private static final Map<String, Integer> _slots = new HashMap<String, Integer>();
	
	static
	{
		_materials.put("paper", L2Item.MATERIAL_PAPER);
		_materials.put("wood", L2Item.MATERIAL_WOOD);
		_materials.put("liquid", L2Item.MATERIAL_LIQUID);
		_materials.put("cloth", L2Item.MATERIAL_CLOTH);
		_materials.put("leather", L2Item.MATERIAL_LEATHER);
		_materials.put("horn", L2Item.MATERIAL_HORN);
		_materials.put("bone", L2Item.MATERIAL_BONE);
		_materials.put("bronze", L2Item.MATERIAL_BRONZE);
		_materials.put("fine_steel", L2Item.MATERIAL_FINE_STEEL);
		_materials.put("cotton", L2Item.MATERIAL_FINE_STEEL);
		_materials.put("mithril", L2Item.MATERIAL_MITHRIL);
		_materials.put("silver", L2Item.MATERIAL_SILVER);
		_materials.put("gold", L2Item.MATERIAL_GOLD);
		_materials.put("adamantaite", L2Item.MATERIAL_ADAMANTAITE);
		_materials.put("steel", L2Item.MATERIAL_STEEL);
		_materials.put("oriharukon", L2Item.MATERIAL_ORIHARUKON);
		_materials.put("blood_steel", L2Item.MATERIAL_BLOOD_STEEL);
		_materials.put("crystal", L2Item.MATERIAL_CRYSTAL);
		_materials.put("damascus", L2Item.MATERIAL_DAMASCUS);
		_materials.put("chrysolite", L2Item.MATERIAL_CHRYSOLITE);
		_materials.put("scale_of_dragon", L2Item.MATERIAL_SCALE_OF_DRAGON);
		_materials.put("dyestuff", L2Item.MATERIAL_DYESTUFF);
		_materials.put("cobweb", L2Item.MATERIAL_COBWEB);
		_materials.put("seed", L2Item.MATERIAL_SEED);
		
		_crystalTypes.put("s84", L2Item.CRYSTAL_S84);
		_crystalTypes.put("s80", L2Item.CRYSTAL_S80);
		_crystalTypes.put("s", L2Item.CRYSTAL_S);
		_crystalTypes.put("a", L2Item.CRYSTAL_A);
		_crystalTypes.put("b", L2Item.CRYSTAL_B);
		_crystalTypes.put("c", L2Item.CRYSTAL_C);
		_crystalTypes.put("d", L2Item.CRYSTAL_D);
		_crystalTypes.put("none", L2Item.CRYSTAL_NONE);
		
		_weaponTypes.put("blunt", L2WeaponType.BLUNT);
		_weaponTypes.put("bow", L2WeaponType.BOW);
		_weaponTypes.put("dagger", L2WeaponType.DAGGER);
		_weaponTypes.put("dual", L2WeaponType.DUAL);
		_weaponTypes.put("dualfist", L2WeaponType.DUALFIST);
		_weaponTypes.put("etc", L2WeaponType.ETC);
		_weaponTypes.put("fist", L2WeaponType.FIST);
		_weaponTypes.put("none", L2WeaponType.NONE); // these are shields !
		_weaponTypes.put("pole", L2WeaponType.POLE);
		_weaponTypes.put("sword", L2WeaponType.SWORD);
		_weaponTypes.put("bigsword", L2WeaponType.BIGSWORD); //Two-Handed Swords
		_weaponTypes.put("pet", L2WeaponType.PET); //Pet Weapon
		_weaponTypes.put("rod", L2WeaponType.ROD); //Fishing Rods
		_weaponTypes.put("bigblunt", L2WeaponType.BIGBLUNT); //Two handed blunt
		_weaponTypes.put("crossbow", L2WeaponType.CROSSBOW);
		_weaponTypes.put("rapier", L2WeaponType.RAPIER);
		_weaponTypes.put("ancient", L2WeaponType.ANCIENT_SWORD);
		_weaponTypes.put("dualdagger", L2WeaponType.DUAL_DAGGER);
		
		_armorTypes.put("none", L2ArmorType.NONE);
		_armorTypes.put("light", L2ArmorType.LIGHT);
		_armorTypes.put("heavy", L2ArmorType.HEAVY);
		_armorTypes.put("magic", L2ArmorType.MAGIC);
		_armorTypes.put("pet", L2ArmorType.PET);
		_armorTypes.put("sigil", L2ArmorType.SIGIL);
		
		_slots.put("shirt", L2Item.SLOT_UNDERWEAR);
		_slots.put("lbracelet", L2Item.SLOT_L_BRACELET);
		_slots.put("rbracelet", L2Item.SLOT_R_BRACELET);
		_slots.put("talisman", L2Item.SLOT_DECO);
		_slots.put("chest", L2Item.SLOT_CHEST);
		_slots.put("fullarmor", L2Item.SLOT_FULL_ARMOR);
		_slots.put("head", L2Item.SLOT_HEAD);
		_slots.put("hair", L2Item.SLOT_HAIR);
		_slots.put("face", L2Item.SLOT_HAIR2);
		_slots.put("hair2", L2Item.SLOT_HAIR2);
		_slots.put("dhair", L2Item.SLOT_HAIRALL);
		_slots.put("hairall", L2Item.SLOT_HAIRALL);
		_slots.put("underwear", L2Item.SLOT_UNDERWEAR);
		_slots.put("back", L2Item.SLOT_BACK);
		_slots.put("neck", L2Item.SLOT_NECK);
		_slots.put("legs", L2Item.SLOT_LEGS);
		_slots.put("feet", L2Item.SLOT_FEET);
		_slots.put("gloves", L2Item.SLOT_GLOVES);
		_slots.put("chest,legs", L2Item.SLOT_CHEST | L2Item.SLOT_LEGS);
		_slots.put("belt", L2Item.SLOT_BELT);
		_slots.put("rhand", L2Item.SLOT_R_HAND);
		_slots.put("lhand", L2Item.SLOT_L_HAND);
		_slots.put("lrhand", L2Item.SLOT_LR_HAND);
		_slots.put("rear,lear", L2Item.SLOT_R_EAR | L2Item.SLOT_L_EAR);
		_slots.put("rfinger,lfinger", L2Item.SLOT_R_FINGER | L2Item.SLOT_L_FINGER);
		_slots.put("wolf", L2Item.SLOT_WOLF);
		_slots.put("greatwolf", L2Item.SLOT_GREATWOLF);
		_slots.put("hatchling", L2Item.SLOT_HATCHLING);
		_slots.put("strider", L2Item.SLOT_STRIDER);
		_slots.put("babypet", L2Item.SLOT_BABYPET);
		_slots.put("none", L2Item.SLOT_NONE);
	}
	
	/** Table of SQL request in order to obtain items from tables [etc_item_template], [armor_template], [weapon_template] */
	private static final String[] SQL_ITEM_SELECTS =
			{
					"SELECT id, name, is_crystallizable, item_type, weight, consume_type, material, crystal_type,"
							+ " shadow_mana, lifetime_s, price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, item_skills, handler_name FROM etc_item_template",
					
					"SELECT id, name, body_part, is_crystallizable, armor_type, weight,"
							+ " material, crystal_type, evasion_modifier, shadow_mana, lifetime_s, physical_defense, magic_defense, mp_bonus,"
							+ " price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, enchant4_skills, item_skills FROM armor_template",
					
					"SELECT id, name, body_part, is_crystallizable, weight, soulshot_count, spiritshot_count,"
							+ " material, crystal_type, physical_damage, random_damage, weapon_type, critical_rate, accuracy_modifier, evasion_modifier,"
							+ " shield_defense, shield_defense_rate, attack_speed, mp_consumption, magic_damage, shadow_mana, lifetime_s, price, crystal_count,"
							+ " is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, item_skills, enchant4_skills,"
							+ " on_cast_skills, on_critical_skills, change_weapon_template_id FROM weapon_template" };
	
	private static final String[] SQL_CUSTOM_ITEM_SELECTS =
			{
					"SELECT id, display_item_id, name, is_crystallizable, item_type, weight, consume_type, material, crystal_type,"
							+ " shadow_mana, lifetime_s, price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, item_skills, handler_name FROM custom_etc_item_template",
					
					"SELECT id, display_item_id, name, body_part, is_crystallizable, armor_type, weight,"
							+ " material, crystal_type, evasion_modifier, shadow_mana, lifetime_s, physical_defense, magic_defense, mp_bonus,"
							+ " price, crystal_count, is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, enchant4_skills, item_skills FROM custom_armor_template",
					
					"SELECT id, display_item_id, name, body_part, is_crystallizable, weight, soulshot_count, spiritshot_count,"
							+ " material, crystal_type, physical_damage, random_damage, weapon_type, critical_rate, accuracy_modifier, evasion_modifier,"
							+ " shield_defense, shield_defense_rate, attack_speed, mp_consumption, magic_damage, shadow_mana, lifetime_s, price, crystal_count,"
							+ " is_sellable, is_droppable, is_destroyable, is_tradable, is_depositable, item_skills, enchant4_skills,"
							+ " on_cast_skills, on_critical_skills, change_weapon_template_id FROM custom_weapon_template" };
	
	private static final class SingletonHolder
	{
		private static ItemTable INSTANCE = new ItemTable();
	}
	
	public static ItemTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public static void reload()
	{
		SingletonHolder.INSTANCE = new ItemTable();
	}
	
	private final L2Item[] _allTemplates;
	
	private ItemTable()
	{
		final Map<Integer, Item> itemData = new HashMap<Integer, Item>();
		final Map<Integer, Item> armorData = new HashMap<Integer, Item>();
		final Map<Integer, Item> weaponData = new HashMap<Integer, Item>();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			for (String selectQuery : SQL_ITEM_SELECTS)
			{
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					if (selectQuery.endsWith("etc_item_template"))
					{
						Item newItem = readItem(rset, false);
						itemData.put(newItem.id, newItem);
					}
					else if (selectQuery.endsWith("armor_template"))
					{
						Item newItem = readArmor(rset, false);
						armorData.put(newItem.id, newItem);
					}
					else if (selectQuery.endsWith("weapon_template"))
					{
						Item newItem = readWeapon(rset, false);
						weaponData.put(newItem.id, newItem);
					}
				}
				
				rset.close();
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warn("data error on item: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			for (String selectQuery : SQL_CUSTOM_ITEM_SELECTS)
			{
				PreparedStatement statement = con.prepareStatement(selectQuery);
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					if (selectQuery.endsWith("etc_item_template"))
					{
						Item newItem = readItem(rset, true);
						itemData.put(newItem.id, newItem);
					}
					else if (selectQuery.endsWith("armor_template"))
					{
						Item newItem = readArmor(rset, true);
						armorData.put(newItem.id, newItem);
					}
					else if (selectQuery.endsWith("weapon_template"))
					{
						Item newItem = readWeapon(rset, true);
						weaponData.put(newItem.id, newItem);
					}
				}
				
				rset.close();
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warn("data error on custom item: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		final List<L2Item> etcItems = DocumentEngine.loadItems(itemData);
		_log.info("ItemTable: Loaded " + etcItems.size() + "/" + etcItems.size() + " Items.");
		
		final List<L2Item> armors = DocumentEngine.loadArmors(armorData);
		_log.info("ItemTable: Loaded " + armors.size() + "/" + armorData.size() + " Armors.");
		
		final List<L2Item> weapons = DocumentEngine.loadWeapons(weaponData);
		_log.info("ItemTable: Loaded " + weapons.size() + "/" + weaponData.size() + " Weapons.");
		
		int highestId = 0;
		for (L2Item item : L2Collections.concatenatedIterable(armors, etcItems, weapons))
			if (highestId < item.getItemId())
				highestId = item.getItemId();
		
		_allTemplates = new L2Item[highestId + 1];
		
		for (L2Item item : L2Collections.concatenatedIterable(armors, etcItems, weapons))
		{
			L2Item old = _allTemplates[item.getItemId()];
			
			_allTemplates[item.getItemId()] = item;
			
			if (old != null)
				_log.error("ItemTable: ID: " + old.getItemId() + " (" + old.getItemType() + " replaced with "
						+ item.getItemType() + ")");
		}
	}
	
	/** @return the shadow item mana in minutes of wear, -1 when the template is not a shadow item */
	private static int getShadowMana(ResultSet rset) throws SQLException
	{
		int mana = rset.getInt("shadow_mana");
		return rset.wasNull() ? -1 : mana;
	}
	
	/** @return the lifetime of the template in whole minutes, rounded up so a limited item never starts expired, -1 when the item has no time limit */
	private static int getLifetimeMinutes(ResultSet rset) throws SQLException
	{
		int seconds = rset.getInt("lifetime_s");
		return rset.wasNull() ? -1 : (seconds + 59) / 60;
	}
	
	/** @return the name of the item handler, "none" when the item has none */
	private static String getHandler(ResultSet rset) throws SQLException
	{
		String handler = rset.getString("handler_name");
		return handler == null ? "none" : handler;
	}
	
	/**
	 * Returns object Item from the record of the database
	 * 
	 * @param rset : ResultSet designating a record of the [weapon] table of database
	 * @return Item : object created from the database record
	 * @throws SQLException
	 */
	private Item readWeapon(ResultSet rset, boolean custom) throws SQLException
	{
		Item item = new Item();
		item.set = new StatsSet();
		item.type = _weaponTypes.get(rset.getString("weapon_type"));
		item.id = rset.getInt("id");
		item.displayid = custom ? rset.getInt("display_item_id") : item.id;
		item.name = rset.getString("name");
		
		item.set.set("item_id", item.id);
		item.set.set("item_display_id", item.displayid);
		item.set.set("name", item.name);
		
		// lets see if this is a shield
		if (item.type == L2WeaponType.NONE)
		{
			item.set.set("type1", L2Item.TYPE1_SHIELD_ARMOR);
			item.set.set("type2", L2Item.TYPE2_SHIELD_ARMOR);
		}
		else
		{
			item.set.set("type1", L2Item.TYPE1_WEAPON_RING_EARRING_NECKLACE);
			item.set.set("type2", L2Item.TYPE2_WEAPON);
		}
		item.set.set("bodypart", _slots.get(rset.getString("body_part")));
		item.set.set("material", _materials.get(rset.getString("material")));
		item.set.set("crystal_type", _crystalTypes.get(rset.getString("crystal_type")));
		item.set.set("crystallizable", rset.getBoolean("is_crystallizable"));
		item.set.set("weight", rset.getInt("weight"));
		item.set.set("soulshots", rset.getInt("soulshot_count"));
		item.set.set("spiritshots", rset.getInt("spiritshot_count"));
		item.set.set("p_dam", rset.getInt("physical_damage"));
		item.set.set("rnd_dam", rset.getInt("random_damage"));
		item.set.set("critical", rset.getInt("critical_rate"));
		item.set.set("hit_modify", (double)rset.getInt("accuracy_modifier"));
		item.set.set("avoid_modify", rset.getInt("evasion_modifier"));
		item.set.set("shield_def", rset.getInt("shield_defense"));
		item.set.set("shield_def_rate", rset.getInt("shield_defense_rate"));
		item.set.set("atk_speed", rset.getInt("attack_speed"));
		item.set.set("mp_consume", rset.getInt("mp_consumption"));
		item.set.set("m_dam", rset.getInt("magic_damage"));
		item.set.set("duration", getShadowMana(rset));
		item.set.set("time", getLifetimeMinutes(rset));
		item.set.set("price", (int)rset.getLong("price"));
		item.set.set("crystal_count", rset.getInt("crystal_count"));
		item.set.set("sellable", rset.getBoolean("is_sellable"));
		item.set.set("dropable", rset.getBoolean("is_droppable"));
		item.set.set("destroyable", rset.getBoolean("is_destroyable"));
		item.set.set("tradeable", rset.getBoolean("is_tradable"));
		item.set.set("depositable", rset.getBoolean("is_depositable"));
		
		item.set.set("skills_item", rset.getString("item_skills"));
		item.set.set("enchant4_skill", rset.getString("enchant4_skills"));
		item.set.set("skills_onCast", rset.getString("on_cast_skills"));
		item.set.set("skills_onCrit", rset.getString("on_critical_skills"));
		item.set.set("change_weaponId", rset.getInt("change_weapon_template_id"));
		
		if (item.type == L2WeaponType.PET)
		{
			item.set.set("type1", L2Item.TYPE1_WEAPON_RING_EARRING_NECKLACE);
			if (item.set.getInteger("bodypart") == L2Item.SLOT_WOLF)
				item.set.set("type2", L2Item.TYPE2_PET_WOLF);
			else if (item.set.getInteger("bodypart") == L2Item.SLOT_GREATWOLF)
				item.set.set("type2", L2Item.TYPE2_PET_EVOLVEDWOLF);
			else if (item.set.getInteger("bodypart") == L2Item.SLOT_HATCHLING)
				item.set.set("type2", L2Item.TYPE2_PET_HATCHLING);
			else if (item.set.getInteger("bodypart") == L2Item.SLOT_BABYPET)
				item.set.set("type2", L2Item.TYPE2_PET_BABY);
			else
				item.set.set("type2", L2Item.TYPE2_PET_STRIDER);
			
			item.set.set("bodypart", L2Item.SLOT_R_HAND);
		}
		
		return item;
	}
	
	/**
	 * Returns object Item from the record of the database
	 * 
	 * @param rset : ResultSet designating a record of the [armor] table of database
	 * @return Item : object created from the database record
	 * @throws SQLException
	 */
	private Item readArmor(ResultSet rset, boolean custom) throws SQLException
	{
		Item item = new Item();
		item.set = new StatsSet();
		item.type = _armorTypes.get(rset.getString("armor_type"));
		item.id = rset.getInt("id");
		item.displayid = custom ? rset.getInt("display_item_id") : item.id;
		item.name = rset.getString("name");
		
		item.set.set("item_id", item.id);
		item.set.set("item_display_id", item.displayid);
		item.set.set("name", item.name);
		int bodypart = _slots.get(rset.getString("body_part"));
		item.set.set("bodypart", bodypart);
		item.set.set("crystallizable", rset.getBoolean("is_crystallizable"));
		item.set.set("crystal_count", rset.getInt("crystal_count"));
		item.set.set("sellable", rset.getBoolean("is_sellable"));
		item.set.set("dropable", rset.getBoolean("is_droppable"));
		item.set.set("destroyable", rset.getBoolean("is_destroyable"));
		item.set.set("tradeable", rset.getBoolean("is_tradable"));
		item.set.set("depositable", rset.getBoolean("is_depositable"));
		item.set.set("enchant4_skill", rset.getString("enchant4_skills"));
		item.set.set("skills_item", rset.getString("item_skills"));
		
		if (bodypart == L2Item.SLOT_NECK || bodypart == L2Item.SLOT_HAIR || bodypart == L2Item.SLOT_HAIR2
				|| bodypart == L2Item.SLOT_HAIRALL || (bodypart & L2Item.SLOT_L_EAR) != 0
				|| (bodypart & L2Item.SLOT_L_FINGER) != 0 || (bodypart & L2Item.SLOT_R_BRACELET) != 0
				|| (bodypart & L2Item.SLOT_L_BRACELET) != 0)
		{
			item.set.set("type1", L2Item.TYPE1_WEAPON_RING_EARRING_NECKLACE);
			item.set.set("type2", L2Item.TYPE2_ACCESSORY);
		}
		else
		{
			item.set.set("type1", L2Item.TYPE1_SHIELD_ARMOR);
			item.set.set("type2", L2Item.TYPE2_SHIELD_ARMOR);
		}
		
		item.set.set("weight", rset.getInt("weight"));
		item.set.set("material", _materials.get(rset.getString("material")));
		item.set.set("crystal_type", _crystalTypes.get(rset.getString("crystal_type")));
		item.set.set("avoid_modify", rset.getInt("evasion_modifier"));
		item.set.set("duration", getShadowMana(rset));
		item.set.set("time", getLifetimeMinutes(rset));
		item.set.set("p_def", rset.getInt("physical_defense"));
		item.set.set("m_def", rset.getInt("magic_defense"));
		item.set.set("mp_bonus", rset.getInt("mp_bonus"));
		item.set.set("price", (int)rset.getLong("price"));
		
		if (item.type == L2ArmorType.PET)
		{
			if (bodypart == L2Item.SLOT_NECK)
			{
				item.set.set("type1", L2Item.TYPE1_WEAPON_RING_EARRING_NECKLACE);
				item.set.set("type2", L2Item.TYPE2_ACCESSORY);
				item.set.set("bodypart", L2Item.SLOT_NECK);
			}
			else
			{
				item.set.set("type1", L2Item.TYPE1_SHIELD_ARMOR);
				switch (item.set.getInteger("bodypart"))
				{
					case L2Item.SLOT_WOLF:
						item.set.set("type2", L2Item.TYPE2_PET_WOLF);
						break;
					case L2Item.SLOT_GREATWOLF:
						item.set.set("type2", L2Item.TYPE2_PET_EVOLVEDWOLF);
						break;
					case L2Item.SLOT_HATCHLING:
						item.set.set("type2", L2Item.TYPE2_PET_HATCHLING);
						break;
					case L2Item.SLOT_BABYPET:
						item.set.set("type2", L2Item.TYPE2_PET_BABY);
						break;
					default:
						item.set.set("type2", L2Item.TYPE2_PET_STRIDER);
						break;
				}
				item.set.set("bodypart", L2Item.SLOT_CHEST);
			}
		}
		
		return item;
	}
	
	/**
	 * Returns object Item from the record of the database
	 * 
	 * @param rset : ResultSet designating a record of the [etcitem] table of database
	 * @return Item : object created from the database record
	 * @throws SQLException
	 */
	private Item readItem(ResultSet rset, boolean custom) throws SQLException
	{
		Item item = new Item();
		item.set = new StatsSet();
		item.id = rset.getInt("id");
		item.displayid = custom ? rset.getInt("display_item_id") : item.id;
		
		item.set.set("item_id", item.id);
		item.set.set("item_display_id", item.displayid);
		item.set.set("crystallizable", rset.getBoolean("is_crystallizable"));
		item.set.set("type1", L2Item.TYPE1_ITEM_QUESTITEM_ADENA);
		item.set.set("type2", L2Item.TYPE2_OTHER);
		item.set.set("bodypart", 0);
		item.set.set("crystal_count", rset.getInt("crystal_count"));
		item.set.set("sellable", rset.getBoolean("is_sellable"));
		item.set.set("dropable", rset.getBoolean("is_droppable"));
		item.set.set("destroyable", rset.getBoolean("is_destroyable"));
		item.set.set("tradeable", rset.getBoolean("is_tradable"));
		item.set.set("depositable", rset.getBoolean("is_depositable"));
		item.set.set("skills_item", rset.getString("item_skills"));
		item.set.set("handler", getHandler(rset));
		
		String itemType = rset.getString("item_type");
		if (itemType.equals("none"))
			item.type = L2EtcItemType.OTHER; // only for default
		else if (itemType.equals("castle_guard"))
			item.type = L2EtcItemType.SCROLL; // dummy
		else if (itemType.equals("material"))
			item.type = L2EtcItemType.MATERIAL;
		else if (itemType.equals("pet_collar"))
			item.type = L2EtcItemType.PET_COLLAR;
		else if (itemType.equals("potion"))
			item.type = L2EtcItemType.POTION;
		else if (itemType.equals("recipe"))
			item.type = L2EtcItemType.RECEIPE;
		else if (itemType.equals("scroll"))
			item.type = L2EtcItemType.SCROLL;
		else if (itemType.equals("seed"))
			item.type = L2EtcItemType.SEED;
		else if (itemType.equals("shot"))
			item.type = L2EtcItemType.SHOT;
		else if (itemType.equals("spellbook"))
			item.type = L2EtcItemType.SPELLBOOK; // Spellbook, Amulet, Blueprint
		else if (itemType.equals("herb"))
			item.type = L2EtcItemType.HERB;
		else if (itemType.equals("arrow"))
		{
			item.type = L2EtcItemType.ARROW;
			item.set.set("bodypart", L2Item.SLOT_L_HAND);
		}
		else if (itemType.equals("bolt"))
		{
			item.type = L2EtcItemType.BOLT;
			item.set.set("bodypart", L2Item.SLOT_L_HAND);
		}
		else if (itemType.equals("quest"))
		{
			item.type = L2EtcItemType.QUEST;
			item.set.set("type2", L2Item.TYPE2_QUEST);
		}
		else if (itemType.equals("lure"))
		{
			item.type = L2EtcItemType.OTHER;
			item.set.set("bodypart", L2Item.SLOT_L_HAND);
		}
		else
		{
			_log.debug("unknown etcitem type:" + itemType);
			item.type = L2EtcItemType.OTHER;
		}
		
		String consume = rset.getString("consume_type");
		if (consume.equals("asset"))
		{
			item.type = L2EtcItemType.MONEY;
			item.set.set("stackable", true);
			item.set.set("type2", L2Item.TYPE2_MONEY);
		}
		else if (consume.equals("stackable"))
		{
			item.set.set("stackable", true);
		}
		else
		{
			item.set.set("stackable", false);
		}
		
		int material = _materials.get(rset.getString("material"));
		item.set.set("material", material);
		
		int crystal = _crystalTypes.get(rset.getString("crystal_type"));
		item.set.set("crystal_type", crystal);
		
		int weight = rset.getInt("weight");
		item.set.set("weight", weight);
		item.name = rset.getString("name");
		item.set.set("name", item.name);
		
		item.set.set("duration", getShadowMana(rset));
		item.set.set("time", getLifetimeMinutes(rset));
		item.set.set("price", (int)rset.getLong("price"));
		
		return item;
	}
	
	/**
	 * Returns the item corresponding to the item ID
	 * 
	 * @param id : int designating the item
	 * @return L2Item
	 */
	public L2Item getTemplate(int id)
	{
		if (id < 0 || _allTemplates.length <= id)
			return null;
		
		return _allTemplates[id];
	}
	
	public <T extends L2Item> T getTemplate(int id, Class<T> clazz)
	{
		L2Item item = getTemplate(id);
		
		if (clazz.isInstance(item))
			return clazz.cast(item);
		else
			return null;
	}
	
	public L2Item[] getAllTemplates()
	{
		return _allTemplates;
	}
	
	/**
	 * Create the L2ItemInstance corresponding to the Item Identifier and quantitiy add logs the activity.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Create and Init the L2ItemInstance corresponding to the Item Identifier and quantity </li>
	 * <li>Add the L2ItemInstance object to _allObjects of L2world </li>
	 * <li>Logs Item creation according to log settings</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be created
	 * @param count : long Quantity of items to be created for stackable items
	 * @param actor : L2Player Player requesting the item creation
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in
	 *            transformation
	 * @return L2ItemInstance corresponding to the new item
	 */
	public L2ItemInstance createItem(String process, int itemId, long count, L2Player actor, L2Object reference)
	{
		// Create and Init the L2ItemInstance corresponding to the Item Identifier
		L2ItemInstance item = new L2ItemInstance(IdFactory.getInstance().getNextId(), itemId);
		
		final boolean isAdena = isAdenaLikeItem(itemId);
		
		if (process.equalsIgnoreCase("loot") && (isAdena ? !Config.ALT_AUTO_LOOT_ADENA : !Config.ALT_AUTO_LOOT))
		{
			long delay;
			// if in CommandChannel and was killing a World/RaidBoss
			if (reference instanceof L2Boss)
			{
				L2CommandChannel commandChannel = ((L2Boss)reference).getFirstCommandChannelAttacked();
				
				if (commandChannel != null && commandChannel.meetRaidWarCondition(reference))
				{
					item.setOwnerId(commandChannel.getChannelLeader().getObjectId());
					delay = 300000;
				}
				else
				{
					item.setOwnerId(actor.getObjectId());
					delay = 15000;
				}
			}
			else
			{
				item.setOwnerId(actor.getObjectId());
				delay = 15000;
			}
			
			item.setItemLootShedule(ThreadPoolManager.getInstance().scheduleGeneral(new ResetOwner(item), delay));
		}
		
		if (_log.isDebugEnabled())
			_log.debug("ItemTable: Item created  oid:" + item.getObjectId() + " itemid:" + itemId);
		
		// Add the L2ItemInstance object to _allObjects of L2world
		L2World.getInstance().storeObject(item);
		
		// Set Item parameters
		if (item.isStackable() && count > 1)
			item.setCount(count);
		
		if (Config.LOG_ITEMS && actor != null)
		{
			List<Object> param = new ArrayList<Object>();
			param.add("CREATE:" + process);
			param.add(item);
			param.add(actor);
			param.add(reference);
			_logItems.info("{}", param);
		}
		
		return item;
	}
	
	public L2ItemInstance createItem(String process, int itemId, long count, L2Player actor)
	{
		return createItem(process, itemId, count, actor, null);
	}
	
	/**
	 * Returns a dummy (fr = factice) item.<BR>
	 * <BR>
	 * <U><I>Concept :</I></U><BR>
	 * Dummy item is created by setting the ID of the object in the world at null value
	 * 
	 * @param itemId : int designating the item
	 * @return L2ItemInstance designating the dummy item created
	 */
	public L2ItemInstance createDummyItem(int itemId)
	{
		L2Item item = getTemplate(itemId);
		if (item == null)
			return null;
		
		return new L2ItemInstance(0, item);
	}
	
	/**
	 * Destroys the L2ItemInstance.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Sets L2ItemInstance parameters to be unusable </li>
	 * <li>Removes the L2ItemInstance object to _allObjects of L2world </li>
	 * <li>Logs Item delettion according to log settings</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance Item Identifier of the item to be created
	 * @param actor : L2Player Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in
	 *            transformation
	 */
	public void destroyItem(String process, L2ItemInstance item, L2Player actor, L2Object reference)
	{
		synchronized (item)
		{
			item.setCount(0);
			item.setOwnerId(0);
			item.setLocation(ItemLocation.VOID);
			item.setLastChange(L2ItemInstance.REMOVED);
			
			L2World.getInstance().removeObject(item);
			IdFactory.getInstance().releaseId(item.getObjectId());
			
			if (Config.LOG_ITEMS && actor != null)
			{
				List<Object> param = new ArrayList<Object>();
				param.add("DELETE:" + process);
				param.add(item);
				param.add(actor);
				param.add(reference);
				_logItems.info("{}", param);
			}
			
			// if it's a pet control item, delete the pet as well
			if (PetDataTable.isPetItem(item.getItemId()))
			{
				try
				{
					// Delete the pet in db
					ItemRepository.getInstance().deletePet(item.getObjectId());
				}
				catch (Exception e)
				{
					_log.warn("could not delete pet objectid:", e);
				}
			}
			
			// delete augmentation data
			item.removeAugmentation();
		}
	}
	
	private static final class ResetOwner implements Runnable
	{
		private final L2ItemInstance _item;
		
		private ResetOwner(L2ItemInstance item)
		{
			_item = item;
		}
		
		@Override
		public void run()
		{
			_item.setOwnerId(0);
			_item.setItemLootShedule(null);
		}
	}
	
	public List<L2Item> findItemsByName(String search)
	{
		search = search.toLowerCase();
		List<L2Item> returnVal = new ArrayList<L2Item>();
		
		if (!search.isEmpty())
		{
			for (L2Item temp : _allTemplates)
			{
				if (temp != null && temp.getName().toLowerCase().contains(search))
				{
					returnVal.add(temp);
				}
			}
		}
		return returnVal;
	}
	
	public static boolean isAdenaLikeItem(int itemId)
	{
		switch (itemId)
		{
			case PlayerInventory.ADENA_ID:
			case PlayerInventory.ANCIENT_ADENA_ID:
			case SevenSigns.SEAL_STONE_BLUE_ID:
			case SevenSigns.SEAL_STONE_GREEN_ID:
			case SevenSigns.SEAL_STONE_RED_ID:
				return true;
			default:
				return false;
		}
	}
	
	public static boolean isSealStone(int itemId)
	{
		switch (itemId)
		{
			case SevenSigns.SEAL_STONE_BLUE_ID:
			case SevenSigns.SEAL_STONE_GREEN_ID:
			case SevenSigns.SEAL_STONE_RED_ID:
				return true;
			default:
				return false;
		}
	}
}
