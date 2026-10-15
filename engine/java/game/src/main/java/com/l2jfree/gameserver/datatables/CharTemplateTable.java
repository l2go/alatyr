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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * This class ...
 * 
 * @version $Revision: 1.6.2.1.2.10 $ $Date: 2005/03/29 14:00:54 $
 */
public class CharTemplateTable
{
	private final static Logger _log = LoggerFactory.getLogger(CharTemplateTable.class);
	
	public static final String[] CHAR_CLASSES = { "Human Fighter", "Warrior", "Gladiator", "Warlord", "Human Knight",
			"Paladin", "Dark Avenger", "Rogue", "Treasure Hunter", "Hawkeye", "Human Mystic", "Human Wizard",
			"Sorceror", "Necromancer", "Warlock", "Cleric", "Bishop", "Prophet", "Elven Fighter", "Elven Knight",
			"Temple Knight", "Swordsinger", "Elven Scout", "Plainswalker", "Silver Ranger", "Elven Mystic",
			"Elven Wizard", "Spellsinger", "Elemental Summoner", "Elven Oracle", "Elven Elder", "Dark Fighter",
			"Palus Knight", "Shillien Knight", "Bladedancer", "Assassin", "Abyss Walker", "Phantom Ranger",
			"Dark Elven Mystic", "Dark Elven Wizard", "Spellhowler", "Phantom Summoner", "Shillien Oracle",
			"Shillien Elder", "Orc Fighter", "Orc Raider", "Destroyer", "Orc Monk", "Tyrant", "Orc Mystic",
			"Orc Shaman", "Overlord", "Warcryer", "Dwarven Fighter", "Dwarven Scavenger", "Bounty Hunter",
			"Dwarven Artisan", "Warsmith", "dummyEntry1", "dummyEntry2", "dummyEntry3", "dummyEntry4", "dummyEntry5",
			"dummyEntry6", "dummyEntry7", "dummyEntry8", "dummyEntry9", "dummyEntry10", "dummyEntry11", "dummyEntry12",
			"dummyEntry13", "dummyEntry14", "dummyEntry15", "dummyEntry16", "dummyEntry17", "dummyEntry18",
			"dummyEntry19", "dummyEntry20", "dummyEntry21", "dummyEntry22", "dummyEntry23", "dummyEntry24",
			"dummyEntry25", "dummyEntry26", "dummyEntry27", "dummyEntry28", "dummyEntry29", "dummyEntry30", "Duelist",
			"Dreadnought", "Phoenix Knight", "Hell Knight", "Sagittarius", "Adventurer", "Archmage", "Soultaker",
			"Arcana Lord", "Cardinal", "Hierophant", "Eva Templar", "Sword Muse", "Wind Rider", "Moonlight Sentinel",
			"Mystic Muse", "Elemental Master", "Eva's Saint", "Shillien Templar", "Spectral Dancer", "Ghost Hunter",
			"Ghost Sentinel", "Storm Screamer", "Spectral Master", "Shillien Saint", "Titan", "Grand Khavatari",
			"Dominator", "Doomcryer", "Fortune Seeker", "Maestro", "dummyEntry31", "dummyEntry32", "dummyEntry33",
			"dummyEntry34", "Male Soldier", "Female Soldier", "Dragoon", "Warder", "Berserker", "Male Soulbreaker",
			"Female Soulbreaker", "Arbalester", "Doombringer", "Male Soulhound", "Female Soulhound", "Trickster",
			"Inspector", "Judicator" };
	
	private final L2PlayerTemplate[] _templates = new L2PlayerTemplate[ClassId.values().length];
	
	public static CharTemplateTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private CharTemplateTable()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT pc.id, pc.race_id, pt.class_name,"
							+ " pt.base_strength, pt.base_constitution, pt.base_dexterity, pt.base_intelligence,"
							+ " pt.base_wit, pt.base_mental,"
							+ " lg.base_hp_max, lg.hp_per_level, lg.hp_per_level_increment,"
							+ " lg.base_mp_max, lg.base_cp_max, lg.cp_per_level, lg.cp_per_level_increment,"
							+ " lg.mp_per_level, lg.mp_per_level_increment,"
							+ " pt.base_physical_attack, pt.base_physical_defense, pt.base_magic_attack,"
							+ " pt.base_magic_defense, lg.class_base_level, pt.base_attack_speed,"
							+ " pt.base_casting_speed, pt.base_critical_rate, pt.base_run_speed,"
							+ " pt.male_collision_radius, pt.male_collision_height,"
							+ " pt.female_collision_radius, pt.female_collision_height"
							+ " FROM player_class pc"
							+ " JOIN player_template pt ON pt.player_class_id = pc.id"
							+ " JOIN level_up_gain lg ON lg.player_class_id = pc.id"
							+ " ORDER BY pc.id");
			ResultSet rset = statement.executeQuery();
			
			int size = 0;
			while (rset.next())
			{
				StatsSet set = new StatsSet();
				set.set("classId", rset.getInt("id"));
				set.set("className", rset.getString("class_name"));
				set.set("raceId", rset.getInt("race_id"));
				set.set("baseSTR", rset.getInt("base_strength"));
				set.set("baseCON", rset.getInt("base_constitution"));
				set.set("baseDEX", rset.getInt("base_dexterity"));
				set.set("baseINT", rset.getInt("base_intelligence"));
				set.set("baseWIT", rset.getInt("base_wit"));
				set.set("baseMEN", rset.getInt("base_mental"));
				set.set("baseHpMax", rset.getFloat("base_hp_max"));
				set.set("lvlHpAdd", rset.getFloat("hp_per_level"));
				set.set("lvlHpMod", rset.getFloat("hp_per_level_increment"));
				set.set("baseMpMax", rset.getFloat("base_mp_max"));
				set.set("baseCpMax", rset.getFloat("base_cp_max"));
				set.set("lvlCpAdd", rset.getFloat("cp_per_level"));
				set.set("lvlCpMod", rset.getFloat("cp_per_level_increment"));
				set.set("lvlMpAdd", rset.getFloat("mp_per_level"));
				set.set("lvlMpMod", rset.getFloat("mp_per_level_increment"));
				set.set("baseHpReg", 1.5);
				set.set("baseMpReg", 0.9);
				set.set("basePAtk", rset.getInt("base_physical_attack"));
				set.set("basePDef", /*classId.isMage()? 77 : 129*/rset.getInt("base_physical_defense"));
				set.set("baseMAtk", rset.getInt("base_magic_attack"));
				set.set("baseMDef", rset.getInt("base_magic_defense"));
				set.set("classBaseLevel", rset.getInt("class_base_level"));
				set.set("basePAtkSpd", rset.getInt("base_attack_speed"));
				set.set("baseMAtkSpd", /*classId.isMage()? 166 : 333*/rset.getInt("base_casting_speed"));
				set.set("baseCritRate", rset.getInt("base_critical_rate") / 10);
				set.set("baseRunSpd", rset.getInt("base_run_speed") * Config.RATE_RUN_SPEED);
				set.set("baseWalkSpd", 0);
				set.set("baseShldDef", 0);
				set.set("baseShldRate", 0);
				set.set("baseAtkRange", 40);
				
				/* Not a single point
				set.set("spawnX", rset.getInt("x"));
				set.set("spawnY", rset.getInt("y"));
				set.set("spawnZ", rset.getInt("z"));
				*/
				
				L2PlayerTemplate ct;
				
				set.set("collision_radius", rset.getDouble("male_collision_radius"));
				set.set("collision_height", rset.getDouble("male_collision_height"));
				// Add-on for females
				set.set("fcollision_radius", rset.getDouble("female_collision_radius"));
				set.set("fcollision_height", rset.getDouble("female_collision_height"));
				ct = new L2PlayerTemplate(set);
				
				_templates[ct.getClassId().getId()] = ct;
				size++;
			}
			
			rset.close();
			statement.close();
			
			_log.info("CharTemplateTable: Loaded " + size + " Character Templates.");
		}
		catch (SQLException e)
		{
			_log.error("Failed loading char templates", e);
		}
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT player_class_id, item_template_id, amount, is_equipped FROM starting_item");
			ResultSet rset = statement.executeQuery();
			
			int classId, itemId, amount;
			boolean equipped;
			while (rset.next())
			{
				classId = rset.getInt("player_class_id");
				if (rset.wasNull())
					classId = -1; // NULL: every class
				itemId = rset.getInt("item_template_id");
				amount = rset.getInt("amount");
				equipped = rset.getBoolean("is_equipped");
				
				if (ItemTable.getInstance().getTemplate(itemId) != null)
				{
					if (classId == -1)
					{
						for (L2PlayerTemplate pct : _templates)
						{
							if (pct == null)
								continue;
							
							pct.addItem(itemId, amount, equipped);
						}
					}
					else
					{
						L2PlayerTemplate pct = _templates[classId];
						if (pct != null)
						{
							pct.addItem(itemId, amount, equipped);
						}
						else
						{
							_log.warn("char_creation_items: Entry for undefined class, classId: " + classId);
						}
					}
				}
				else
				{
					_log.warn("char_creation_items: No data for itemId: " + itemId + " defined for classId " + classId);
				}
			}
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Failed loading char creation items.", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public L2PlayerTemplate getTemplate(ClassId classId)
	{
		return getTemplate(classId.getId());
	}
	
	public L2PlayerTemplate getTemplate(int classId)
	{
		if (classId < 0 || classId >= _templates.length)
			return null;
		return _templates[classId];
	}
	
	public static final String getClassNameById(int classId)
	{
		return CHAR_CLASSES[classId];
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CharTemplateTable _instance = new CharTemplateTable();
	}
}
