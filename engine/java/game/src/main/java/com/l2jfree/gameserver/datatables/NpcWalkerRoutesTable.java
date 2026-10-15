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
import com.l2jfree.gameserver.model.L2NpcWalkerNode;

/**
 * Main Table to Load Npc Walkers Routes and Chat SQL Table.<br>
 * 
 * @author Rayan RPG for L2Emu Project
 *
 */
public class NpcWalkerRoutesTable
{
	private final static Logger _log = LoggerFactory.getLogger(SpawnTable.class);
	
	private final List<L2NpcWalkerNode> _routes = new ArrayList<L2NpcWalkerNode>();
	
	public static NpcWalkerRoutesTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private NpcWalkerRoutesTable()
	{
		_log.info("Initializing Walker Routes Table.");
	}
	
	public void load()
	{
		_routes.clear();
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT route_number, npc_template_id, point_number, chat_text, x, y, z, delay_s, is_running FROM walker_route ORDER BY point_number ASC");
			ResultSet rset = statement.executeQuery();
			L2NpcWalkerNode route;
			while (rset.next())
			{
				route = new L2NpcWalkerNode();
				route.setRouteId(rset.getInt("route_number"));
				route.setNpcId(rset.getInt("npc_template_id"));
				route.setMovePoint(Integer.toString(rset.getInt("point_number")));
				route.setChatText(rset.getString("chat_text"));
				
				route.setMoveX(rset.getInt("x"));
				route.setMoveY(rset.getInt("y"));
				route.setMoveZ(rset.getInt("z"));
				route.setDelay(rset.getInt("delay_s"));
				route.setRunning(rset.getBoolean("is_running"));
				
				_routes.add(route);
			}
			
			rset.close();
			statement.close();
			
			_log.info("WalkerRoutesTable: Loaded " + _routes.size() + " Npc Walker Routes.");
		}
		catch (Exception e)
		{
			_log.error("WalkerRoutesTable: Error while loading Npc Walker Routes: " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public ArrayList<L2NpcWalkerNode> getRouteForNpc(int id)
	{
		ArrayList<L2NpcWalkerNode> _return = new ArrayList<L2NpcWalkerNode>();
		
		for (L2NpcWalkerNode n : _routes)
		{
			if (n.getNpcId() == id)
			{
				_return.add(n);
			}
		}
		return _return;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final NpcWalkerRoutesTable _instance = new NpcWalkerRoutesTable();
	}
}
