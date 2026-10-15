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

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.L2TeleportLocation;

/**
 * This class ...
 *
 * @version $Revision: 1.3.2.2.2.3 $ $Date: 2005/03/27 15:29:18 $
 */
public class TeleportLocationTable
{
	private final static Logger _log = LoggerFactory.getLogger(TeleportLocationTable.class);
	
	private Map<Integer, L2TeleportLocation> _teleports;
	
	public static TeleportLocationTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private TeleportLocationTable()
	{
		reloadAll();
	}
	
	public void reloadAll()
	{
		_teleports = new LinkedHashMap<Integer, L2TeleportLocation>();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, x, y, z, price, is_noble_only FROM teleport");
			ResultSet rset = statement.executeQuery();
			L2TeleportLocation teleport;
			
			while (rset.next())
			{
				teleport = new L2TeleportLocation();
				
				teleport.setTeleId(rset.getInt("id"));
				teleport.setLocX(rset.getInt("x"));
				teleport.setLocY(rset.getInt("y"));
				teleport.setLocZ(rset.getInt("z"));
				if (Config.ALT_GAME_FREE_TELEPORT)
					teleport.setPrice(0);
				else
					teleport.setPrice(rset.getInt("price"));
				teleport.setIsForNoble(rset.getBoolean("is_noble_only"));
				
				_teleports.put(teleport.getTeleId(), teleport);
			}
			
			rset.close();
			statement.close();
			
			_log.info("TeleportLocationTable: Loaded " + _teleports.size() + " Teleport Location Templates.");
		}
		catch (Exception e)
		{
			_log.error("error while creating teleport table ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * @param template id
	 * @return
	 */
	public L2TeleportLocation getTemplate(int id)
	{
		return _teleports.get(id);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final TeleportLocationTable _instance = new TeleportLocationTable();
	}
}
