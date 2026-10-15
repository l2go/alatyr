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
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.model.L2LvlupData;

/**
 * This class ...
 * 
 * @author NightMarez
 * @version $Revision: 1.3.2.4.2.3 $ $Date: 2005/03/27 15:29:18 $
 */
public class LevelUpData
{
	private static final String SELECT_ALL =
			"SELECT player_class_id, base_hp_max, hp_per_level, hp_per_level_increment, base_cp_max, cp_per_level, cp_per_level_increment, base_mp_max, mp_per_level, mp_per_level_increment, class_base_level FROM level_up_gain";
	private static final String CLASS_LVL = "class_base_level";
	private static final String MP_MOD = "mp_per_level_increment";
	private static final String MP_ADD = "mp_per_level";
	private static final String MP_BASE = "base_mp_max";
	private static final String HP_MOD = "hp_per_level_increment";
	private static final String HP_ADD = "hp_per_level";
	private static final String HP_BASE = "base_hp_max";
	private static final String CP_MOD = "cp_per_level_increment";
	private static final String CP_ADD = "cp_per_level";
	private static final String CP_BASE = "base_cp_max";
	private static final String CLASS_ID = "player_class_id";
	
	private final static Logger _log = LoggerFactory.getLogger(LevelUpData.class);
	
	private final Map<Integer, L2LvlupData> _lvlTable;
	
	public static LevelUpData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private LevelUpData()
	{
		_lvlTable = new LinkedHashMap<Integer, L2LvlupData>();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(SELECT_ALL);
			ResultSet rset = statement.executeQuery();
			L2LvlupData lvlDat;
			
			while (rset.next())
			{
				lvlDat = new L2LvlupData();
				lvlDat.setClassid(rset.getInt(CLASS_ID));
				lvlDat.setClassLvl(rset.getInt(CLASS_LVL));
				lvlDat.setClassHpBase(rset.getFloat(HP_BASE));
				lvlDat.setClassHpAdd(rset.getFloat(HP_ADD));
				lvlDat.setClassHpModifier(rset.getFloat(HP_MOD));
				lvlDat.setClassCpBase(rset.getFloat(CP_BASE));
				lvlDat.setClassCpAdd(rset.getFloat(CP_ADD));
				lvlDat.setClassCpModifier(rset.getFloat(CP_MOD));
				lvlDat.setClassMpBase(rset.getFloat(MP_BASE));
				lvlDat.setClassMpAdd(rset.getFloat(MP_ADD));
				lvlDat.setClassMpModifier(rset.getFloat(MP_MOD));
				
				_lvlTable.put(lvlDat.getClassid(), lvlDat);
			}
			
			rset.close();
			statement.close();
			
			_log.info("LevelUpData: Loaded " + _lvlTable.size() + " Character Level Up Templates.");
		}
		catch (Exception e)
		{
			_log.error("error while creating Lvl up data table ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @param classId
	 * @return
	 */
	public L2LvlupData getTemplate(int classId)
	{
		return _lvlTable.get(classId);
	}
	
	public L2LvlupData getTemplate(ClassId classId)
	{
		return _lvlTable.get(classId.getId());
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final LevelUpData _instance = new LevelUpData();
	}
}
