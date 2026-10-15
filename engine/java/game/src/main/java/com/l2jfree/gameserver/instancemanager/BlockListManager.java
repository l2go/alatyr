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
package com.l2jfree.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.lang.L2Integer;
import com.l2jfree.util.LazyFastSet;

/**
 * @author NB4L1
 */
public final class BlockListManager
{
	private static final Logger _log = LoggerFactory.getLogger(BlockListManager.class);
	
	// The blocks are kept in memory by name; the table stores the blocked player's id
	private static final String SELECT_QUERY =
			"SELECT b.player_id, p.name FROM player_block b JOIN player p ON p.id = b.blocked_player_id";
	private static final String INSERT_QUERY =
			"INSERT INTO player_block (player_id, blocked_player_id) VALUES (?,?) ON CONFLICT DO NOTHING";
	private static final String DELETE_QUERY =
			"DELETE FROM player_block WHERE player_id=? AND blocked_player_id = (SELECT id FROM player WHERE name=?)";
	
	public static BlockListManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, Set<String>> _blocks = new HashMap<Integer, Set<String>>();
	
	private BlockListManager()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(SELECT_QUERY);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				Integer objectId = L2Integer.valueOf(rset.getInt("player_id"));
				String name = rset.getString("name");
				
				getBlockList(objectId).add(name);
			}
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		int size = 0;
		
		for (Set<String> set : _blocks.values())
			size += set.size();
		
		_log.info("BlockListManager: Loaded " + size + " character block(s).");
	}
	
	public synchronized Set<String> getBlockList(Integer objectId)
	{
		Set<String> set = _blocks.get(objectId);
		
		if (set == null)
			_blocks.put(objectId, set = new LazyFastSet<String>());
		
		return set;
	}
	
	public synchronized void insert(L2Player listOwner, L2Player blocked)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(INSERT_QUERY);
			statement.setInt(1, listOwner.getObjectId());
			statement.setInt(2, blocked.getObjectId());
			
			statement.execute();
			
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public synchronized void remove(L2Player listOwner, String name)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(DELETE_QUERY);
			statement.setInt(1, listOwner.getObjectId());
			statement.setString(2, name);
			
			statement.execute();
			
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final BlockListManager _instance = new BlockListManager();
	}
}
