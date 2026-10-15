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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.FishData;

/**
 * @author -Nemesiss-
 *
 */
public class FishTable
{
	private final static Logger _log = LoggerFactory.getLogger(SkillTreeTable.class);
	
	private static List<FishData> _fishsNormal;
	private static List<FishData> _fishsEasy;
	private static List<FishData> _fishsHard;
	
	public static FishTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private FishTable()
	{
		//Create table that contains all fish datas
		int count = 0;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			_fishsEasy = new ArrayList<FishData>();
			_fishsNormal = new ArrayList<FishData>();
			_fishsHard = new ArrayList<FishData>();
			FishData fish;
			PreparedStatement statement =
					con.prepareStatement("SELECT item_template_id, level, name, hp, hp_regeneration, fish_type, fish_group, guts, guts_check_interval_ms, bite_wait_ms, combat_duration_ms FROM fish ORDER BY item_template_id");
			ResultSet Fishes = statement.executeQuery();
			
			while (Fishes.next())
			{
				int id = Fishes.getInt("item_template_id");
				int lvl = Fishes.getInt("level");
				String name = Fishes.getString("name");
				int hp = Fishes.getInt("hp");
				int hpreg = Fishes.getInt("hp_regeneration");
				int type = Fishes.getInt("fish_type");
				int group = Fishes.getInt("fish_group");
				int fish_guts = Fishes.getInt("guts");
				int guts_check_time = Fishes.getInt("guts_check_interval_ms");
				int wait_time = Fishes.getInt("bite_wait_ms");
				int combat_time = Fishes.getInt("combat_duration_ms");
				fish =
						new FishData(id, lvl, name, hp, hpreg, type, group, fish_guts, guts_check_time, wait_time,
								combat_time);
				switch (fish.getGroup())
				{
					case 0:
						_fishsEasy.add(fish);
						break;
					case 1:
						_fishsNormal.add(fish);
						break;
					case 2:
						_fishsHard.add(fish);
				}
			}
			Fishes.close();
			statement.close();
			count = _fishsEasy.size() + _fishsNormal.size() + _fishsHard.size();
		}
		catch (Exception e)
		{
			_log.error("error while creating fishes table", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("FishTable: Loaded " + count + " Fishes.");
	}
	
	/**
	 * @param Fish - lvl
	 * @param Fish - type
	 * @param Fish - group
	 * @return List of Fish that can be fished
	 */
	public List<FishData> getFish(int lvl, int type, int group)
	{
		List<FishData> result = new ArrayList<FishData>();
		List<FishData> _fishs = null;
		switch (group)
		{
			case 0:
				_fishs = _fishsEasy;
				break;
			case 1:
				_fishs = _fishsNormal;
				break;
			case 2:
				_fishs = _fishsHard;
		}
		if (_fishs == null)
		{
			// the fish list is empty
			_log.warn("Fish are not defined !");
			return null;
		}
		for (FishData f : _fishs)
		{
			if (f.getLevel() != lvl)
				continue;
			if (f.getType() != type)
				continue;
			
			result.add(f);
		}
		if (result.size() == 0)
			_log.warn("FishTable: Fishes are not definied for Lvl " + lvl + " type " + type + " group " + group);
		return result;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final FishTable _instance = new FishTable();
	}
}
