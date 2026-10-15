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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Summon;

public class PetSkillsTable
{
	private final static Logger _log = LoggerFactory.getLogger(PetSkillsTable.class);
	
	private final Map<Integer, Map<Integer, L2PetSkillLearn>> _skillTrees;
	
	public static PetSkillsTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private PetSkillsTable()
	{
		_skillTrees = new LinkedHashMap<Integer, Map<Integer, L2PetSkillLearn>>();
		load();
	}
	
	public void reload()
	{
		_skillTrees.clear();
		load();
	}
	
	private void load()
	{
		int npcId = 0;
		int count = 0;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			try
			{
				PreparedStatement statement =
						con.prepareStatement("SELECT id FROM npc_template WHERE instance_type IN ('L2Pet','L2BabyPet','L2SiegeSummon') ORDER BY id");
				ResultSet petlist = statement.executeQuery();
				Map<Integer, L2PetSkillLearn> map;
				L2PetSkillLearn skillLearn;
				while (petlist.next())
				{
					map = new LinkedHashMap<Integer, L2PetSkillLearn>();
					npcId = petlist.getInt("id");
					PreparedStatement statement2 =
							con.prepareStatement("SELECT min_level, skill_id, skill_level FROM pet_skill WHERE npc_template_id=? ORDER BY skill_id, skill_level");
					statement2.setInt(1, npcId);
					ResultSet skilltree = statement2.executeQuery();
					
					while (skilltree.next())
					{
						int id = skilltree.getInt("skill_id");
						int lvl = skilltree.getInt("skill_level");
						int minLvl = skilltree.getInt("min_level");
						
						skillLearn = new L2PetSkillLearn(id, lvl, minLvl);
						map.put(SkillTable.getSkillUID(id, lvl + 1), skillLearn);
					}
					_skillTrees.put(npcId, map);
					skilltree.close();
					statement2.close();
					
					count += map.size();
					if (_log.isDebugEnabled())
						_log.info("PetSkillsTable: Skill tree for pet " + npcId + " has " + map.size() + " skills");
				}
				petlist.close();
				statement.close();
			}
			catch (Exception e)
			{
				_log.error("Error while creating pet skill tree (Pet ID " + npcId + "):", e);
			}
			_log.info("PetSkillsTable: Loaded " + count + " skills.");
		}
		catch (Exception e)
		{
			_log.error("Error while loading pet skills tables ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public int getAvailableLevel(L2Summon cha, int skillId)
	{
		int lvl = 0;
		if (!_skillTrees.containsKey(cha.getNpcId()))
			return lvl;
		Collection<L2PetSkillLearn> skills = _skillTrees.get(cha.getNpcId()).values();
		for (L2PetSkillLearn temp : skills)
		{
			if (temp.getId() != skillId)
				continue;
			if (temp.getLevel() == 0)
			{
				if (cha.getLevel() < 70)
				{
					lvl = (cha.getLevel() / 10);
					if (lvl <= 0)
						lvl = 1;
				}
				else
					lvl = (7 + ((cha.getLevel() - 70) / 5));
				
				// formula usable for skill that have 10 or more skill levels
				lvl = Math.min(lvl, SkillTable.getInstance().getMaxLevel(temp.getId()));
				break;
			}
			else if (temp.getMinLevel() <= cha.getLevel())
			{
				if (temp.getLevel() > lvl)
					lvl = temp.getLevel();
			}
		}
		return lvl;
	}
	
	public List<Integer> getAvailableSkills(L2Summon cha)
	{
		List<Integer> skillIds = new ArrayList<Integer>();
		if (!_skillTrees.containsKey(cha.getNpcId()))
			return null;
		Collection<L2PetSkillLearn> skills = _skillTrees.get(cha.getNpcId()).values();
		for (L2PetSkillLearn temp : skills)
		{
			if (skillIds.contains(temp.getId()))
				continue;
			skillIds.add(temp.getId());
		}
		return skillIds;
	}
	
	public final class L2PetSkillLearn
	{
		private final int _id;
		private final int _level;
		private final int _minLevel;
		
		public L2PetSkillLearn(int id, int lvl, int minLvl)
		{
			_id = id;
			_level = lvl;
			_minLevel = minLvl;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public int getLevel()
		{
			return _level;
		}
		
		public int getMinLevel()
		{
			return _minLevel;
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final PetSkillsTable _instance = new PetSkillsTable();
	}
}
