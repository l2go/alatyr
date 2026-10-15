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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.templates.L2Henna;

public class HennaTreeTable
{
	private static final Logger _log = LoggerFactory.getLogger(HennaTreeTable.class);
	
	private final Map<Integer, L2Henna[]> _hennaTrees = new LinkedHashMap<Integer, L2Henna[]>();
	
	public static HennaTreeTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private HennaTreeTable()
	{
		int classId = 0;
		int count = 0;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT id FROM player_class");
			ResultSet classlist = statement.executeQuery();
			
			while (classlist.next())
			{
				classId = classlist.getInt("id");
				List<L2Henna> list = new ArrayList<L2Henna>();
				
				PreparedStatement statement2 =
						con.prepareStatement("SELECT henna_id FROM henna_class WHERE player_class_id = ?");
				statement2.setInt(1, classId);
				ResultSet hennatree = statement2.executeQuery();
				
				while (hennatree.next())
				{
					int id = hennatree.getInt("henna_id");
					
					L2Henna template = HennaTable.getInstance().getTemplate(id);
					if (template == null)
					{
						hennatree.close();
						statement2.close();
						classlist.close();
						statement.close();
						return;
					}
					
					list.add(template);
				}
				hennatree.close();
				statement2.close();
				
				count += list.size();
				_hennaTrees.put(classId, list.toArray(new L2Henna[list.size()]));
			}
			
			classlist.close();
			statement.close();
			_log.info("HennaTreeTable: Loaded " + count + " Henna Tree Templates.");
		}
		catch (Exception e)
		{
			_log.warn("Error while creating henna tree for classId " + classId + " ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public L2Henna[] getAvailableHenna(L2Player player)
	{
		return _hennaTrees.get(player.getClassId().getId());
	}
	
	/**
	 * Prevents henna drawing exploit:
	 * 1) talk to L2SymbolMakerInstance
	 * 2) RequestHennaList
	 * 3) Don't close the window and go to a GrandMaster and change your subclass
	 * 4) Get SymbolMaker range again and press draw
	 * You could draw any kind of henna just having the required subclass...
	 * @param activeChar a player (if it is null, returns false)
	 * @param symbolId henna dye ID
	 */
	public final boolean isDrawable(L2Player player, int symbolId)
	{
		if (player == null)
			return false;
		
		for (L2Henna h : getAvailableHenna(player))
			if (h.getSymbolId() == symbolId)
				return true;
		return false;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final HennaTreeTable _instance = new HennaTreeTable();
	}
}
