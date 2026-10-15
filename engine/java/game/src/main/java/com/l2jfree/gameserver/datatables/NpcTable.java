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
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.L2MinionData;
import com.l2jfree.gameserver.model.drop.L2DropCategory;
import com.l2jfree.gameserver.model.drop.L2DropData;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.util.LookupTable;

public final class NpcTable
{
	private static final Logger _log = LoggerFactory.getLogger(NpcTable.class);
	
	public static NpcTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/** Columns of npc_template and custom_npc_template that fillNpcTable reads (both tables have the same columns). */
	private static final String NPC_COLUMNS = "id, client_template_id, client_class, name, sends_server_name, title, sends_server_title, collision_radius, collision_height, level, sex, instance_type, attack_range, max_hp, max_mp, hp_regen, mp_regen, strength, constitution, dexterity, intelligence, wit, mental, reward_exp, reward_sp, p_atk, p_def, m_atk, m_def, p_atk_speed, aggro_range, m_atk_speed, right_hand_item_template_id, left_hand_item_template_id, armor_item_template_id, walk_speed, run_speed, faction, faction_range, is_undead, absorb_level, absorb_type, soulshot_count, blessed_spiritshot_count, shot_chance, ai_type, drops_herbs";
	
	private static final String SELECT_NPC = "SELECT " + NPC_COLUMNS + " FROM npc_template";
	private static final String SELECT_CUSTOM_NPC = "SELECT " + NPC_COLUMNS + " FROM custom_npc_template";
	private static final String SELECT_NPC_BY_ID = SELECT_NPC + " WHERE id = ?";
	private static final String SELECT_CUSTOM_NPC_BY_ID = SELECT_CUSTOM_NPC + " WHERE id = ?";
	
	/** How the value of an NPC property is bound. */
	private enum Kind
	{
		/** text */
		TEXT,
		/** text; an empty value is stored as NULL */
		TEXT_OR_NULL,
		/** whole number */
		INT,
		/** whole number; 0 is stored as NULL */
		INT_OR_NULL,
		/** flag; 0 is false, anything else is true */
		FLAG
	}
	
	/**
	 * The NPC properties an admin can change (AdminEditNpc), by the key AdminEditNpc puts into the StatsSet. Every
	 * property has its own constant statement for each of the two NPC tables, so no statement is built at run time.
	 */
	private enum NpcColumn
	{
		CLIENT_TEMPLATE_ID("idTemplate", Kind.INT,
			"UPDATE npc_template SET client_template_id = ? WHERE id = ?",
			"UPDATE custom_npc_template SET client_template_id = ? WHERE id = ?"),
		NAME("name", Kind.TEXT,
			"UPDATE npc_template SET name = ? WHERE id = ?",
			"UPDATE custom_npc_template SET name = ? WHERE id = ?"),
		SENDS_SERVER_NAME("serverSideName", Kind.FLAG,
			"UPDATE npc_template SET sends_server_name = ? WHERE id = ?",
			"UPDATE custom_npc_template SET sends_server_name = ? WHERE id = ?"),
		TITLE("title", Kind.TEXT,
			"UPDATE npc_template SET title = ? WHERE id = ?",
			"UPDATE custom_npc_template SET title = ? WHERE id = ?"),
		SENDS_SERVER_TITLE("serverSideTitle", Kind.FLAG,
			"UPDATE npc_template SET sends_server_title = ? WHERE id = ?",
			"UPDATE custom_npc_template SET sends_server_title = ? WHERE id = ?"),
		COLLISION_RADIUS("collision_radius", Kind.INT,
			"UPDATE npc_template SET collision_radius = ? WHERE id = ?",
			"UPDATE custom_npc_template SET collision_radius = ? WHERE id = ?"),
		COLLISION_HEIGHT("collision_height", Kind.INT,
			"UPDATE npc_template SET collision_height = ? WHERE id = ?",
			"UPDATE custom_npc_template SET collision_height = ? WHERE id = ?"),
		LEVEL("level", Kind.INT,
			"UPDATE npc_template SET level = ? WHERE id = ?",
			"UPDATE custom_npc_template SET level = ? WHERE id = ?"),
		SEX("sex", Kind.TEXT,
			"UPDATE npc_template SET sex = ? WHERE id = ?",
			"UPDATE custom_npc_template SET sex = ? WHERE id = ?"),
		INSTANCE_TYPE("type", Kind.TEXT,
			"UPDATE npc_template SET instance_type = ? WHERE id = ?",
			"UPDATE custom_npc_template SET instance_type = ? WHERE id = ?"),
		ATTACK_RANGE("attackrange", Kind.INT,
			"UPDATE npc_template SET attack_range = ? WHERE id = ?",
			"UPDATE custom_npc_template SET attack_range = ? WHERE id = ?"),
		MAX_HP("hp", Kind.INT,
			"UPDATE npc_template SET max_hp = ? WHERE id = ?",
			"UPDATE custom_npc_template SET max_hp = ? WHERE id = ?"),
		MAX_MP("mp", Kind.INT,
			"UPDATE npc_template SET max_mp = ? WHERE id = ?",
			"UPDATE custom_npc_template SET max_mp = ? WHERE id = ?"),
		HP_REGEN("hpreg", Kind.INT_OR_NULL,
			"UPDATE npc_template SET hp_regen = ? WHERE id = ?",
			"UPDATE custom_npc_template SET hp_regen = ? WHERE id = ?"),
		MP_REGEN("mpreg", Kind.INT_OR_NULL,
			"UPDATE npc_template SET mp_regen = ? WHERE id = ?",
			"UPDATE custom_npc_template SET mp_regen = ? WHERE id = ?"),
		STRENGTH("str", Kind.INT,
			"UPDATE npc_template SET strength = ? WHERE id = ?",
			"UPDATE custom_npc_template SET strength = ? WHERE id = ?"),
		CONSTITUTION("con", Kind.INT,
			"UPDATE npc_template SET constitution = ? WHERE id = ?",
			"UPDATE custom_npc_template SET constitution = ? WHERE id = ?"),
		DEXTERITY("dex", Kind.INT,
			"UPDATE npc_template SET dexterity = ? WHERE id = ?",
			"UPDATE custom_npc_template SET dexterity = ? WHERE id = ?"),
		INTELLIGENCE("int", Kind.INT,
			"UPDATE npc_template SET intelligence = ? WHERE id = ?",
			"UPDATE custom_npc_template SET intelligence = ? WHERE id = ?"),
		WIT("wit", Kind.INT,
			"UPDATE npc_template SET wit = ? WHERE id = ?",
			"UPDATE custom_npc_template SET wit = ? WHERE id = ?"),
		MENTAL("men", Kind.INT,
			"UPDATE npc_template SET mental = ? WHERE id = ?",
			"UPDATE custom_npc_template SET mental = ? WHERE id = ?"),
		REWARD_EXP("exp", Kind.INT,
			"UPDATE npc_template SET reward_exp = ? WHERE id = ?",
			"UPDATE custom_npc_template SET reward_exp = ? WHERE id = ?"),
		REWARD_SP("sp", Kind.INT,
			"UPDATE npc_template SET reward_sp = ? WHERE id = ?",
			"UPDATE custom_npc_template SET reward_sp = ? WHERE id = ?"),
		P_ATK("patk", Kind.INT,
			"UPDATE npc_template SET p_atk = ? WHERE id = ?",
			"UPDATE custom_npc_template SET p_atk = ? WHERE id = ?"),
		P_DEF("pdef", Kind.INT,
			"UPDATE npc_template SET p_def = ? WHERE id = ?",
			"UPDATE custom_npc_template SET p_def = ? WHERE id = ?"),
		M_ATK("matk", Kind.INT,
			"UPDATE npc_template SET m_atk = ? WHERE id = ?",
			"UPDATE custom_npc_template SET m_atk = ? WHERE id = ?"),
		M_DEF("mdef", Kind.INT,
			"UPDATE npc_template SET m_def = ? WHERE id = ?",
			"UPDATE custom_npc_template SET m_def = ? WHERE id = ?"),
		P_ATK_SPEED("atkspd", Kind.INT,
			"UPDATE npc_template SET p_atk_speed = ? WHERE id = ?",
			"UPDATE custom_npc_template SET p_atk_speed = ? WHERE id = ?"),
		AGGRO_RANGE("aggro", Kind.INT,
			"UPDATE npc_template SET aggro_range = ? WHERE id = ?",
			"UPDATE custom_npc_template SET aggro_range = ? WHERE id = ?"),
		M_ATK_SPEED("matkspd", Kind.INT,
			"UPDATE npc_template SET m_atk_speed = ? WHERE id = ?",
			"UPDATE custom_npc_template SET m_atk_speed = ? WHERE id = ?"),
		RIGHT_HAND_ITEM_TEMPLATE_ID("rhand", Kind.INT_OR_NULL,
			"UPDATE npc_template SET right_hand_item_template_id = ? WHERE id = ?",
			"UPDATE custom_npc_template SET right_hand_item_template_id = ? WHERE id = ?"),
		LEFT_HAND_ITEM_TEMPLATE_ID("lhand", Kind.INT_OR_NULL,
			"UPDATE npc_template SET left_hand_item_template_id = ? WHERE id = ?",
			"UPDATE custom_npc_template SET left_hand_item_template_id = ? WHERE id = ?"),
		ARMOR_ITEM_TEMPLATE_ID("armor", Kind.INT_OR_NULL,
			"UPDATE npc_template SET armor_item_template_id = ? WHERE id = ?",
			"UPDATE custom_npc_template SET armor_item_template_id = ? WHERE id = ?"),
		WALK_SPEED("walkspd", Kind.INT,
			"UPDATE npc_template SET walk_speed = ? WHERE id = ?",
			"UPDATE custom_npc_template SET walk_speed = ? WHERE id = ?"),
		RUN_SPEED("runspd", Kind.INT,
			"UPDATE npc_template SET run_speed = ? WHERE id = ?",
			"UPDATE custom_npc_template SET run_speed = ? WHERE id = ?"),
		FACTION("faction_id", Kind.TEXT_OR_NULL,
			"UPDATE npc_template SET faction = ? WHERE id = ?",
			"UPDATE custom_npc_template SET faction = ? WHERE id = ?"),
		FACTION_RANGE("faction_range", Kind.INT,
			"UPDATE npc_template SET faction_range = ? WHERE id = ?",
			"UPDATE custom_npc_template SET faction_range = ? WHERE id = ?"),
		IS_UNDEAD("isUndead", Kind.FLAG,
			"UPDATE npc_template SET is_undead = ? WHERE id = ?",
			"UPDATE custom_npc_template SET is_undead = ? WHERE id = ?"),
		ABSORB_LEVEL("absorb_level", Kind.INT,
			"UPDATE npc_template SET absorb_level = ? WHERE id = ?",
			"UPDATE custom_npc_template SET absorb_level = ? WHERE id = ?"),
		ABSORB_TYPE("absorb_type", Kind.TEXT,
			"UPDATE npc_template SET absorb_type = ? WHERE id = ?",
			"UPDATE custom_npc_template SET absorb_type = ? WHERE id = ?");
		
		private static final Map<String, NpcColumn> BY_KEY = new java.util.HashMap<String, NpcColumn>();
		
		static
		{
			for (NpcColumn column : values())
				BY_KEY.put(column._key.toLowerCase(), column);
		}
		
		private final String _key;
		private final Kind _kind;
		private final String _updateNpc;
		private final String _updateCustomNpc;
		
		private NpcColumn(String key, Kind kind, String updateNpc, String updateCustomNpc)
		{
			_key = key;
			_kind = kind;
			_updateNpc = updateNpc;
			_updateCustomNpc = updateCustomNpc;
		}
		
		static NpcColumn forKey(String key)
		{
			return BY_KEY.get(key.toLowerCase());
		}
		
		void bind(PreparedStatement statement, String value) throws SQLException
		{
			switch (_kind)
			{
				case TEXT:
					statement.setString(1, value);
					break;
				case TEXT_OR_NULL:
					if (value.isEmpty())
						statement.setNull(1, Types.VARCHAR);
					else
						statement.setString(1, value);
					break;
				case INT:
					statement.setInt(1, Integer.parseInt(value));
					break;
				case INT_OR_NULL:
				{
					int number = Integer.parseInt(value);
					if (number == 0)
						statement.setNull(1, Types.INTEGER);
					else
						statement.setInt(1, number);
					break;
				}
				case FLAG:
					statement.setBoolean(1, Integer.parseInt(value) != 0);
					break;
			}
		}
	}
	
	private final LookupTable<L2NpcTemplate> _npcs = new LookupTable<L2NpcTemplate>();
	
	private NpcTable()
	{
		restoreNpcData();
	}
	
	private void restoreNpcData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			try
			{
				PreparedStatement statement = con.prepareStatement(SELECT_NPC);
				ResultSet npcdata = statement.executeQuery();
				fillNpcTable(npcdata);
				npcdata.close();
				statement.close();
				_log.info("NpcTable: Loaded " + _npcs.size() + " Npc Templates.");
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error creating NPC table: ", e);
			}
			
			try
			{
				PreparedStatement statement = con.prepareStatement(SELECT_CUSTOM_NPC);
				ResultSet npcdata = statement.executeQuery();
				int npc_count = _npcs.size();
				fillNpcTable(npcdata);
				npcdata.close();
				statement.close();
				if (_npcs.size() > npc_count)
					_log.info("NpcTable: Loaded " + (_npcs.size() - npc_count) + " Custom Npc Templates.");
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error creating custom NPC table: ", e);
			}
			
			try
			{
				PreparedStatement statement = con.prepareStatement("SELECT npc_template_id, skill_id, skill_level FROM npc_skill");
				ResultSet npcskills = statement.executeQuery();
				
				while (npcskills.next())
				{
					int mobId = npcskills.getInt("npc_template_id");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					
					if (npcDat == null)
					{
						if (Config.ALT_DEV_VERIFY_NPC_SKILLS)
							_log.warn("NpcTable: Missing template for npcskills for npc id: " + mobId);
						continue;
					}
					
					int skillId = npcskills.getInt("skill_id");
					int level = npcskills.getInt("skill_level");
					
					if (skillId == 4416)
					{
						npcDat.setRace(level);
						continue;
					}
					
					L2Skill npcSkill = SkillTable.getInstance().getInfo(skillId, level);
					
					if (npcSkill == null)
					{
						if (Config.ALT_DEV_VERIFY_NPC_SKILLS)
							_log.warn("NpcTable: Missing skill for npcskills for skill id-lvl: " + skillId + " "
									+ level);
						continue;
					}
					
					npcDat.addSkill(npcSkill);
				}
				
				npcskills.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC skills table: ", e);
			}
			
			try
			{
				PreparedStatement statement =
						con.prepareStatement("SELECT npc_template_id, skill_id, skill_level FROM custom_npc_skill");
				ResultSet npcskills = statement.executeQuery();
				
				while (npcskills.next())
				{
					int mobId = npcskills.getInt("npc_template_id");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					
					if (npcDat == null)
					{
						_log.warn("NpcTable: Missing template for custom_npcskills for npc id: " + mobId);
						continue;
					}
					
					int skillId = npcskills.getInt("skill_id");
					int level = npcskills.getInt("skill_level");
					
					if (skillId == 4416)
					{
						npcDat.setRace(level);
						continue;
					}
					
					L2Skill npcSkill = SkillTable.getInstance().getInfo(skillId, level);
					
					if (npcSkill == null)
					{
						_log.warn("NpcTable: Missing skill for custom_npcskills for skill id-lvl: " + skillId + " "
								+ level);
						continue;
					}
					
					npcDat.addSkill(npcSkill);
				}
				
				npcskills.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading custom NPC skills table: ", e);
			}
			
			try
			{
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_template_id, item_template_id, min_count, max_count, category, chance FROM drop ORDER BY npc_template_id, chance DESC");
				ResultSet dropData = statement2.executeQuery();
				
				while (dropData.next())
				{
					int mobId = dropData.getInt("npc_template_id");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					if (npcDat == null)
					{
						_log.error("NPCTable: Drop data for undefined NPC. npcId: " + mobId);
						continue;
					}
					L2DropData dropDat = new L2DropData();
					
					dropDat.setItemId(dropData.getInt("item_template_id"));
					dropDat.setMinDrop(dropData.getInt("min_count"));
					dropDat.setMaxDrop(dropData.getInt("max_count"));
					dropDat.setChance(dropData.getInt("chance"));
					
					int category = dropData.getInt("category");
					
					npcDat.addDropData(dropDat, category);
				}
				
				dropData.close();
				statement2.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC drop data: ", e);
			}
			
			try
			{
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_template_id, item_template_id, min_count, max_count, category, chance FROM custom_drop ORDER BY npc_template_id, chance DESC");
				ResultSet dropData = statement2.executeQuery();
				
				while (dropData.next())
				{
					int mobId = dropData.getInt("npc_template_id");
					L2NpcTemplate npcDat = _npcs.get(mobId);
					if (npcDat == null)
					{
						_log.error("NPCTable: Custom drop data for undefined NPC. npcId: " + mobId);
						continue;
					}
					L2DropData dropDat = new L2DropData();
					
					dropDat.setItemId(dropData.getInt("item_template_id"));
					dropDat.setMinDrop(dropData.getInt("min_count"));
					dropDat.setMaxDrop(dropData.getInt("max_count"));
					dropDat.setChance(dropData.getInt("chance"));
					
					int category = dropData.getInt("category");
					
					npcDat.addDropData(dropDat, category);
				}
				
				dropData.close();
				statement2.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading custom NPC drop data: ", e);
			}
			
			try
			{
				PreparedStatement statement3 = con.prepareStatement("SELECT npc_template_id, player_class_id FROM skill_trainer_class");
				ResultSet learndata = statement3.executeQuery();
				
				while (learndata.next())
				{
					int npcId = learndata.getInt("npc_template_id");
					int classId = learndata.getInt("player_class_id");
					L2NpcTemplate npc = getTemplate(npcId);
					
					if (npc == null)
					{
						_log.warn("NPCTable: Error getting NPC template ID " + npcId
								+ " while trying to load skill trainer data.");
						continue;
					}
					
					npc.addTeachInfo(ClassId.values()[classId]);
				}
				
				learndata.close();
				statement3.close();
			}
			catch (Exception e)
			{
				_log.error("NPCTable: Error reading NPC trainer data: ", e);
			}
			
			try
			{
				PreparedStatement statement4 = con.prepareStatement("SELECT boss_npc_template_id, minion_npc_template_id, min_count, max_count FROM minion");
				ResultSet minionData = statement4.executeQuery();
				int cnt = 0;
				
				while (minionData.next())
				{
					int raidId = minionData.getInt("boss_npc_template_id");
					L2NpcTemplate npcDat = _npcs.get(raidId);
					if (npcDat == null)
					{
						_log.warn("Minion references undefined boss NPC. Boss NpcId: " + raidId);
						continue;
					}
					L2MinionData minionDat = new L2MinionData();
					minionDat.setMinionId(minionData.getInt("minion_npc_template_id"));
					minionDat.setAmountMin(minionData.getInt("min_count"));
					minionDat.setAmountMax(minionData.getInt("max_count"));
					npcDat.addRaidData(minionDat);
					cnt++;
				}
				
				minionData.close();
				statement4.close();
				_log.info("NpcTable: Loaded " + cnt + " Minions.");
			}
			catch (Exception e)
			{
				_log.error("Error loading minion data: ", e);
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private boolean fillNpcTable(ResultSet NpcData) throws Exception
	{
		boolean loaded = false;
		while (NpcData.next())
		{
			StatsSet npcDat = new StatsSet();
			int id = NpcData.getInt("id");
			
			if (Config.ASSERT)
				assert id < 1000000;
			
			npcDat.set("npcId", id);
			npcDat.set("idTemplate", NpcData.getInt("client_template_id"));
			int level = NpcData.getInt("level");
			npcDat.set("level", level);
			npcDat.set("jClass", NpcData.getString("client_class"));
			
			npcDat.set("baseShldDef", 0);
			npcDat.set("baseShldRate", 0);
			npcDat.set("baseCritRate", 38);
			
			npcDat.set("name", NpcData.getString("name"));
			npcDat.set("serverSideName", NpcData.getBoolean("sends_server_name"));
			npcDat.set("title", NpcData.getString("title"));
			npcDat.set("serverSideTitle", NpcData.getBoolean("sends_server_title"));
			npcDat.set("collision_radius", NpcData.getDouble("collision_radius"));
			npcDat.set("collision_height", NpcData.getDouble("collision_height"));
			npcDat.set("fcollision_radius", NpcData.getDouble("collision_radius"));
			npcDat.set("fcollision_height", NpcData.getDouble("collision_height"));
			npcDat.set("sex", NpcData.getString("sex"));
			if (!Config.ALLOW_NPC_WALKERS && NpcData.getString("instance_type").equalsIgnoreCase("L2NpcWalker"))
				npcDat.set("type", "L2Npc");
			else
				npcDat.set("type", NpcData.getString("instance_type"));
			npcDat.set("baseAtkRange", NpcData.getInt("attack_range"));
			npcDat.set("rewardExp", NpcData.getInt("reward_exp"));
			npcDat.set("rewardSp", NpcData.getInt("reward_sp"));
			npcDat.set("basePAtkSpd", NpcData.getInt("p_atk_speed"));
			npcDat.set("baseMAtkSpd", NpcData.getInt("m_atk_speed"));
			npcDat.set("aggroRange", NpcData.getInt("aggro_range"));
			npcDat.set("rhand", NpcData.getInt("right_hand_item_template_id"));
			npcDat.set("lhand", NpcData.getInt("left_hand_item_template_id"));
			npcDat.set("armor", NpcData.getInt("armor_item_template_id"));
			npcDat.set("baseWalkSpd", NpcData.getInt("walk_speed"));
			npcDat.set("baseRunSpd", NpcData.getInt("run_speed"));
			
			npcDat.safeSet("baseSTR", NpcData.getInt("strength"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseCON", NpcData.getInt("constitution"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseDEX", NpcData.getInt("dexterity"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseINT", NpcData.getInt("intelligence"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseWIT", NpcData.getInt("wit"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			npcDat.safeSet("baseMEN", NpcData.getInt("mental"), 0, Formulas.MAX_STAT_VALUE, "Loading NPC template; ID: "
					+ npcDat.getString("idTemplate"));
			
			npcDat.set("baseHpMax", NpcData.getInt("max_hp"));
			npcDat.set("baseCpMax", 0);
			npcDat.set("baseMpMax", NpcData.getInt("max_mp"));
			npcDat.set("baseHpReg", NpcData.getFloat("hp_regen") > 0 ? NpcData.getFloat("hp_regen")
					: 1.5 + ((level - 1) / 10.0));
			npcDat.set("baseMpReg", NpcData.getFloat("mp_regen") > 0 ? NpcData.getFloat("mp_regen")
					: 0.9 + 0.3 * ((level - 1) / 10.0));
			npcDat.set("basePAtk", NpcData.getInt("p_atk"));
			npcDat.set("basePDef", NpcData.getInt("p_def"));
			npcDat.set("baseMAtk", NpcData.getInt("m_atk"));
			npcDat.set("baseMDef", NpcData.getInt("m_def"));
			
			// faction is NULL for an NPC without a faction; the template then keeps no faction id
			String faction = NpcData.getString("faction");
			if (faction != null)
				npcDat.set("factionId", faction);
			npcDat.set("factionRange", NpcData.getInt("faction_range"));
			
			npcDat.set("isUndead", NpcData.getBoolean("is_undead") ? 1 : 0);
			
			npcDat.set("absorb_level", NpcData.getInt("absorb_level"));
			npcDat.set("absorb_type", NpcData.getString("absorb_type"));
			
			npcDat.set("ss", NpcData.getInt("soulshot_count"));
			npcDat.set("bss", NpcData.getInt("blessed_spiritshot_count"));
			npcDat.set("ssRate", NpcData.getInt("shot_chance"));
			
			npcDat.set("AI", NpcData.getString("ai_type"));
			npcDat.set("drop_herbs", NpcData.getBoolean("drops_herbs"));
			
			L2NpcTemplate template = new L2NpcTemplate(npcDat);
			template.addVulnerability(Stats.BOW_WPN_VULN, 1);
			template.addVulnerability(Stats.CROSSBOW_WPN_VULN, 1);
			template.addVulnerability(Stats.BLUNT_WPN_VULN, 1);
			template.addVulnerability(Stats.DAGGER_WPN_VULN, 1);
			
			_npcs.set(id, template);
			
			loaded = true;
		}
		return loaded;
	}
	
	public boolean reloadNpc(int id)
	{
		Connection con = null;
		boolean loaded = false;
		try
		{
			// save a copy of the old data
			L2NpcTemplate old = getTemplate(id);
			Map<Integer, L2Skill> skills = null;
			
			// L2NpcTemplate.getSkillS() is unmodifiable, so the entrySet() of it can't be used
			if (old != null && old.getSkills() != null)
			{
				skills = new LinkedHashMap<Integer, L2Skill>(old.getSkills().size());
				
				for (Integer key : old.getSkills().keySet())
					skills.put(key, old.getSkills().get(key));
			}
			
			L2DropCategory[] categories = new L2DropCategory[0];
			
			if (old != null && old.getDropData() != null)
				categories = ArrayUtils.addAll(categories, old.getDropData());
			
			List<ClassId> classIds = new ArrayList<ClassId>();
			
			if (old != null && old.getTeachInfo() != null)
				classIds.addAll(old.getTeachInfo());
			
			L2MinionData[] minions = new L2MinionData[0];
			
			if (old != null && old.getMinionData() != null)
				minions = ArrayUtils.addAll(minions, old.getMinionData());
			
			// reload the NPC base data
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement st = con.prepareStatement(SELECT_NPC_BY_ID);
			st.setInt(1, id);
			ResultSet rs = st.executeQuery();
			loaded = fillNpcTable(rs);
			rs.close();
			st.close();
			
			if (!loaded)
			{
				st = con.prepareStatement(SELECT_CUSTOM_NPC_BY_ID);
				st.setInt(1, id);
				rs = st.executeQuery();
				loaded = fillNpcTable(rs);
				rs.close();
				st.close();
			}
			
			// restore additional data from saved copy
			L2NpcTemplate created = getTemplate(id);
			
			if (skills != null)
				for (L2Skill skill : skills.values())
					created.addSkill(skill);
			
			for (ClassId classId : classIds)
				created.addTeachInfo(classId);
			
			for (L2MinionData minion : minions)
				created.addRaidData(minion);
		}
		catch (Exception e)
		{
			_log.warn("NPCTable: Could not reload data for NPC " + id + ": " + e, e);
			loaded = false;
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return loaded;
	}
	
	// just wrapper
	public void reloadAll()
	{
		reloadAll(true);
	}
	
	public void reloadAll(boolean reloadQuests)
	{
		restoreNpcData();
		if (reloadQuests)
			QuestManager.getInstance().reloadAllQuests();
	}
	
	public void cleanUp()
	{
		_npcs.clear(false);
	}
	
	public void saveNpc(StatsSet npc)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			Map<String, Object> set = npc.getSet();
			int npcId = npc.getInteger("npcId");
			
			for (Map.Entry<String, Object> entry : set.entrySet())
			{
				if (entry.getKey().equalsIgnoreCase("npcId"))
					continue;
				
				NpcColumn column = NpcColumn.forKey(entry.getKey());
				if (column == null)
				{
					_log.warn("NpcTable: Unknown NPC property " + entry.getKey() + ", not stored.");
					continue;
				}
				
				// the NPC is in one of the two tables (or both), the other update changes no row
				updateNpc(con, column._updateNpc, column, String.valueOf(entry.getValue()), npcId);
				updateNpc(con, column._updateCustomNpc, column, String.valueOf(entry.getValue()), npcId);
			}
		}
		catch (Exception e)
		{
			_log.warn("NPCTable: Could not store new NPC data in database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void updateNpc(Connection con, String sql, NpcColumn column, String value, int npcId)
	{
		try
		{
			PreparedStatement statement = con.prepareStatement(sql);
			column.bind(statement, value);
			statement.setInt(2, npcId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public L2NpcTemplate getTemplate(int id)
	{
		return _npcs.get(id);
	}
	
	public Iterable<L2NpcTemplate> getAllTemplates()
	{
		return _npcs;
	}
	
	public L2NpcTemplate getTemplateByName(String name)
	{
		for (L2NpcTemplate npcTemplate : _npcs)
			if (npcTemplate.getName().equalsIgnoreCase(name))
				return npcTemplate;
		return null;
	}
	
	public L2NpcTemplate[] getAllOfLevel(int lvl)
	{
		List<L2NpcTemplate> list = new ArrayList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getLevel() == lvl)
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	public L2NpcTemplate[] getAllMonstersOfLevel(int lvl)
	{
		List<L2NpcTemplate> list = new ArrayList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getLevel() == lvl && t.isAssignableTo(L2MonsterInstance.class))
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	public L2NpcTemplate[] getAllNpcStartingWith(String letter)
	{
		List<L2NpcTemplate> list = new ArrayList<L2NpcTemplate>();
		for (L2NpcTemplate t : _npcs)
			if (t.getName().startsWith(letter) && t.isAssignableTo(L2Npc.class))
				list.add(t);
		return list.toArray(new L2NpcTemplate[list.size()]);
	}
	
	/**
	 * @param classType
	 * @return
	 */
	public Set<Integer> getAllNpcOfClassType(String classType)
	{
		return null;
	}
	
	/**
	 * @param clazz
	 * @return
	 */
	public Set<Integer> getAllNpcOfL2jClass(Class<?> clazz)
	{
		return null;
	}
	
	/**
	 * @param aiType
	 * @return
	 */
	public Set<Integer> getAllNpcOfAiType(String aiType)
	{
		return null;
	}
	
	public List<L2NpcTemplate> getMobsByDrop(int itemid)
	{
		List<L2NpcTemplate> returnVal = new ArrayList<L2NpcTemplate>();
		for (L2NpcTemplate tempNpc : _npcs)
		{
			List<L2DropData> dropdata = tempNpc.getAllDropData();
			if (dropdata != null)
			{
				for (L2DropData tempDrop : dropdata)
				{
					if (tempDrop.getItemId() == itemid)
					{
						returnVal.add(tempNpc);
						break;
					}
				}
			}
		}
		return returnVal;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final NpcTable _instance = new NpcTable();
	}
}
