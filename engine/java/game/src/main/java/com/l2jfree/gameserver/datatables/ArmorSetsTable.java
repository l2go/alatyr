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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.L2ArmorSet;

/**
 * @author Luno & Psychokiller1888
 */
public final class ArmorSetsTable
{
	private static final Logger _log = LoggerFactory.getLogger(ArmorSetsTable.class);
	
	private final Map<Integer, L2ArmorSet> _armorSets;
	
	private ArmorSetsTable()
	{
		_armorSets = new ConcurrentHashMap<Integer, L2ArmorSet>();
		loadData();
	}
	
	private void loadData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT chest_armor_template_id, legs_armor_template_id, head_armor_template_id, gloves_armor_template_id, feet_armor_template_id, set_skills, shield_weapon_template_id, shield_skill_id, enchant6_skill_id, masterwork_legs_armor_template_id, masterwork_head_armor_template_id, masterwork_gloves_armor_template_id, masterwork_feet_armor_template_id, masterwork_shield_weapon_template_id FROM armor_set");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				int chest = rset.getInt("chest_armor_template_id");
				int legs = rset.getInt("legs_armor_template_id");
				int head = rset.getInt("head_armor_template_id");
				int gloves = rset.getInt("gloves_armor_template_id");
				int feet = rset.getInt("feet_armor_template_id");
				String[] skills = rset.getString("set_skills").split(";");
				int shield = rset.getInt("shield_weapon_template_id");
				int shield_skill_id = rset.getInt("shield_skill_id");
				int enchant6skill = rset.getInt("enchant6_skill_id");
				int mwork_legs = rset.getInt("masterwork_legs_armor_template_id");
				int mwork_head = rset.getInt("masterwork_head_armor_template_id");
				int mwork_gloves = rset.getInt("masterwork_gloves_armor_template_id");
				int mwork_feet = rset.getInt("masterwork_feet_armor_template_id");
				int mwork_shield = rset.getInt("masterwork_shield_weapon_template_id");
				_armorSets.put(chest, new L2ArmorSet(chest, legs, head, gloves, feet, skills, shield, shield_skill_id,
						enchant6skill, mwork_legs, mwork_head, mwork_gloves, mwork_feet, mwork_shield));
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Could not completely load armor sets!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
			_log.info("ArmorSetsTable: Loaded " + _armorSets.size() + " armor sets.");
		}
	}
	
	public boolean setExists(int chestId)
	{
		return _armorSets.containsKey(chestId);
	}
	
	public L2ArmorSet getSet(int chestId)
	{
		return _armorSets.get(chestId);
	}
	
	public static ArmorSetsTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ArmorSetsTable _instance = new ArmorSetsTable();
	}
}
