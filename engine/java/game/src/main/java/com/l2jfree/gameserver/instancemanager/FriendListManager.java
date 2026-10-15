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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.lang.L2Integer;
import com.l2jfree.util.LazyFastSet;

/**
 * @author NB4L1
 */
public final class FriendListManager
{
	private static final Logger _log = LoggerFactory.getLogger(FriendListManager.class);
	
	private static final String SELECT_QUERY =
			"SELECT player_id, friend_player_id FROM player_friendship WHERE player_id=? OR friend_player_id=?";
	// A friendship is stored once, the lower player id first
	private static final String INSERT_QUERY =
			"INSERT INTO player_friendship (player_id, friend_player_id) VALUES (?,?) ON CONFLICT DO NOTHING";
	private static final String DELETE_QUERY = "DELETE FROM player_friendship WHERE player_id=? AND friend_player_id=?";
	
	public static FriendListManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, Set<Integer>> _friends = new LinkedHashMap<Integer, Set<Integer>>();
	
	private FriendListManager()
	{
		_log.info("FriendListManager: initialized.");
	}
	
	public synchronized Set<Integer> getFriendList(Integer objectId)
	{
		Set<Integer> set = _friends.get(objectId);
		
		if (set == null)
		{
			_friends.put(objectId, set = new LazyFastSet<Integer>());
			
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				
				PreparedStatement statement = con.prepareStatement(SELECT_QUERY);
				statement.setInt(1, objectId);
				statement.setInt(2, objectId);
				
				ResultSet rset = statement.executeQuery();
				
				while (rset.next())
				{
					Integer objId1 = L2Integer.valueOf(rset.getInt("player_id"));
					Integer objId2 = L2Integer.valueOf(rset.getInt("friend_player_id"));
					
					Set<Integer> set1 = _friends.get(objId1);
					if (set1 != null)
						set1.add(objId2);
					
					Set<Integer> set2 = _friends.get(objId2);
					if (set2 != null)
						set2.add(objId1);
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
		}
		
		return set;
	}
	
	public synchronized boolean insert(Integer objId1, Integer objId2)
	{
		boolean modified = false;
		
		modified |= _friends.containsKey(objId1) && _friends.get(objId1).add(objId2);
		modified |= _friends.containsKey(objId2) && _friends.get(objId2).add(objId1);
		
		if (!modified)
			return false;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(INSERT_QUERY);
			statement.setInt(1, Math.min(objId1, objId2));
			statement.setInt(2, Math.max(objId1, objId2));
			
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
		
		return true;
	}
	
	public synchronized boolean remove(Integer objId1, Integer objId2)
	{
		boolean modified = false;
		
		modified |= _friends.containsKey(objId1) && _friends.get(objId1).remove(objId2);
		modified |= _friends.containsKey(objId2) && _friends.get(objId2).remove(objId1);
		
		if (!modified)
			return false;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement(DELETE_QUERY);
			statement.setInt(1, Math.min(objId1, objId2));
			statement.setInt(2, Math.max(objId1, objId2));
			
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
		
		return true;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final FriendListManager _instance = new FriendListManager();
	}
}
