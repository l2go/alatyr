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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.model.skills.templates.L2BuffTemplate;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * This class represents the buff templates list
 * @author G1ta0
 */
public class BuffTemplateTable
{
	private final static Logger _log = LoggerFactory.getLogger(BuffTemplateTable.class);
	
	/** The table containing all buff templates */
	private final Map<Integer, TemplateList> _templates;
	
	/**
	 * Create and Load the buff templates from SQL Table buff_templates
	 */
	private BuffTemplateTable()
	{
		_templates = new ConcurrentHashMap<Integer, TemplateList>();
		reloadBuffTemplates();
	}
	
	/**
	 * Read and Load the buff templates from SQL Table buff_templates
	 */
	public void reloadBuffTemplates()
	{
		_templates.clear();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT t.id, t.name, s.skill_id, s.skill_level, s.position, s.is_force_cast, s.min_player_level, s.max_player_level, s.race_mask, s.class_kind, s.required_faction, s.price_adena, s.price_faction_points FROM buff_template t JOIN buff_template_skill s ON s.buff_template_id = t.id ORDER BY t.id, s.position");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				StatsSet buff = new StatsSet();
				
				int templateId = rset.getInt("id");
				buff.set("id", templateId);
				buff.set("name", rset.getString("name"));
				buff.set("skillId", rset.getInt("skill_id"));
				buff.set("skillLevel", rset.getInt("skill_level"));
				buff.set("skillOrder", rset.getInt("position"));
				buff.set("forceCast", rset.getBoolean("is_force_cast") ? 1 : 0);
				buff.set("minLevel", rset.getInt("min_player_level"));
				buff.set("maxLevel", rset.getInt("max_player_level"));
				buff.set("race", rset.getInt("race_mask"));
				buff.set("class", rset.getInt("class_kind"));
				buff.set("faction", rset.getInt("required_faction"));
				buff.set("adena", rset.getInt("price_adena"));
				buff.set("points", rset.getInt("price_faction_points"));
				
				// Add this buff template to the buff template list
				L2BuffTemplate template = new L2BuffTemplate(buff);
				if (template.getSkill() != null)
				{
					TemplateList list = getBuffTemplate(templateId);
					if (list == TemplateList.EMPTY_LIST)
					{
						list = new TemplateList(templateId, template.getName());
						_templates.put(templateId, list);
					}
					list.add(template);
				}
				else
					_log.warn("Skill doesn't exist: " + template.getSkillId() + " Lv" + template.getSkillLevel(),
							new IllegalArgumentException());
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Could not completely load buff templates!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
			_log.info("BuffTemplateTable: Loaded " + _templates.size() + " buff templates.");
		}
	}
	
	/**
	 * @return Returns the buffs of template by template Id
	 */
	public TemplateList getBuffTemplate(int id)
	{
		TemplateList list = _templates.get(id);
		if (list == null)
			return TemplateList.EMPTY_LIST;
		else
			return list;
	}
	
	/**
	 * @return Returns the template Id by template Name
	 */
	public int getTemplateIdByName(String name)
	{
		for (TemplateList list : _templates.values())
		{
			if (list.getName().equals(name))
				return list.getId();
		}
		return -1;
	}
	
	/**
	 * @return Returns the lowest char level for Buff template
	 */
	public int getLowestLevel(int templateId)
	{
		return getBuffTemplate(templateId).getLvlMin();
	}
	
	/**
	 * @return Returns the highest char level for Buff template
	 */
	public int getHighestLevel(int templateId)
	{
		return getBuffTemplate(templateId).getLvlMax();
	}
	
	/**
	 * @return Returns the buff templates
	 */
	public Map<Integer, TemplateList> getBuffTemplateTable()
	{
		return _templates;
	}
	
	public static class TemplateList
	{
		private static final TemplateList EMPTY_LIST = new TemplateList(0, "<<<Empty>>>");
		
		private final int _id;
		private final String _name;
		private int _lvlMin;
		private int _lvlMax;
		private final List<L2BuffTemplate> _buffs;
		
		private TemplateList(int id, String name)
		{
			_id = id;
			_name = name;
			_lvlMin = Experience.MAX_LEVEL;
			_lvlMax = 0;
			_buffs = new ArrayList<L2BuffTemplate>();
		}
		
		private void add(L2BuffTemplate bt)
		{
			if (bt == null || bt.getId() != getId() || !bt.getName().equals(getName()))
			{
				_log.warn("", new IllegalArgumentException());
				return;
			}
			getBuffs().add(bt);
			if (bt.getMinLevel() < getLvlMin())
				_lvlMin = bt.getMinLevel();
			if (bt.getMaxLevel() > getLvlMax())
				_lvlMax = bt.getMaxLevel();
		}
		
		public final int getId()
		{
			return _id;
		}
		
		public final String getName()
		{
			return _name;
		}
		
		public final int getLvlMin()
		{
			return _lvlMin;
		}
		
		public final int getLvlMax()
		{
			return _lvlMax;
		}
		
		public final List<L2BuffTemplate> getBuffs()
		{
			return _buffs;
		}
	}
	
	public static BuffTemplateTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final BuffTemplateTable _instance = new BuffTemplateTable();
	}
}
