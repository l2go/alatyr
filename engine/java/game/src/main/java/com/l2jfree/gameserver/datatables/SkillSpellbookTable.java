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

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.skills.L2Skill;

public class SkillSpellbookTable
{
	private final static Logger _log = LoggerFactory.getLogger(SkillTreeTable.class);
	
	private final Map<Integer, Integer> _skillSpellbooks;
	
	public static SkillSpellbookTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private SkillSpellbookTable()
	{
		_skillSpellbooks = new ConcurrentHashMap<Integer, Integer>();
		
		if (!Config.ALT_SP_BOOK_NEEDED)
			return;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT skill_id, item_template_id FROM skill_spellbook");
			ResultSet spbooks = statement.executeQuery();
			
			while (spbooks.next())
				_skillSpellbooks.put(spbooks.getInt("skill_id"), spbooks.getInt("item_template_id"));
			
			spbooks.close();
			statement.close();
			
			_log.info("SkillSpellbookTable: Loaded " + _skillSpellbooks.size() + " Spellbooks.");
		}
		catch (Exception e)
		{
			_log.warn("Error while loading spellbook data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public int getBookForSkill(int skillId, int level)
	{
		if (skillId == L2Skill.SKILL_DIVINE_INSPIRATION && level != -1)
		{
			switch (level)
			{
				case 1:
					return 8618; // Ancient Book - Divine Inspiration (Modern Language Version)
				case 2:
					return 8619; // Ancient Book - Divine Inspiration (Original Language Version)
				case 3:
					return 8620; // Ancient Book - Divine Inspiration (Manuscript)
				case 4:
					return 8621; // Ancient Book - Divine Inspiration (Original Version)
				default:
					return -1;
			}
		}
		
		if (!Config.ALT_SP_BOOK_NEEDED)
			return -1;
		
		if (!_skillSpellbooks.containsKey(skillId))
			return -1;
		
		return _skillSpellbooks.get(skillId);
	}
	
	public int getBookForSkill(L2Skill skill)
	{
		return getBookForSkill(skill.getId(), -1);
	}
	
	public int getBookForSkill(L2Skill skill, int level)
	{
		return getBookForSkill(skill.getId(), level);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SkillSpellbookTable _instance = new SkillSpellbookTable();
	}
}
