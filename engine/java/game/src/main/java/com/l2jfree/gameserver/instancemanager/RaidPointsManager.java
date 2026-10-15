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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * @author Kerberos
 */
public final class RaidPointsManager
{
	private static final Logger _log = LoggerFactory.getLogger(RaidPointsManager.class);
	
	private static final Map<Integer, Map<Integer, Integer>> _list = new ConcurrentHashMap<Integer, Map<Integer, Integer>>();
	
	public static void init()
	{
		_list.clear();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT player_id, boss_npc_template_id, points FROM player_raid_score");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
				getList(rset.getInt("player_id")).put(rset.getInt("boss_npc_template_id"), rset.getInt("points"));
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private static Map<Integer, Integer> getList(int objectId)
	{
		Map<Integer, Integer> pointsByBossId = _list.get(objectId);
		
		if (pointsByBossId == null)
			_list.put(objectId, pointsByBossId = new HashMap<Integer, Integer>());
		
		return pointsByBossId;
	}
	
	public static void addPoints(L2Player player, int bossId, int points)
	{
		final Map<Integer, Integer> pointsByBossId = getList(player.getObjectId());
		
		points += pointsByBossId.containsKey(bossId) ? pointsByBossId.get(bossId).intValue() : 0;
		
		pointsByBossId.put(bossId, points);
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement =
					con.prepareStatement("INSERT INTO player_raid_score (player_id, boss_npc_template_id, points) VALUES (?,?,?)"
								+ " ON CONFLICT (player_id, boss_npc_template_id) DO UPDATE SET points = EXCLUDED.points");
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, bossId);
			statement.setInt(3, points);
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("could not update char raid points:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static int getPointsByOwnerId(int ownerId)
	{
		Map<Integer, Integer> tmpPoint = _list.get(ownerId);
		
		if (tmpPoint == null || tmpPoint.isEmpty())
			return 0;
		
		int totalPoints = 0;
		
		for (int points : tmpPoint.values())
			totalPoints += points;
		
		return totalPoints;
	}
	
	public static Map<Integer, Integer> getList(L2Player player)
	{
		return _list.get(player.getObjectId());
	}
	
	public static void cleanUp()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("DELETE FROM player_raid_score");
			statement.executeUpdate();
			statement.close();
			_list.clear();
		}
		catch (Exception e)
		{
			_log.error("could not clean raid points: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public static int calculateRanking(int playerObjId)
	{
		Integer value = getRankList().get(playerObjId);
		
		return value == null ? 0 : value.intValue();
	}
	
	@SuppressWarnings("unchecked")
	public static Map<Integer, Integer> getRankList()
	{
		final Map<Integer, Integer> pointsByOwnerId = new HashMap<Integer, Integer>();
		
		for (int ownerId : _list.keySet())
		{
			int totalPoints = getPointsByOwnerId(ownerId);
			if (totalPoints != 0)
				pointsByOwnerId.put(ownerId, totalPoints);
		}
		
		final Entry<Integer, Integer>[] entries = pointsByOwnerId.entrySet().toArray(new Entry[pointsByOwnerId.size()]);
		
		Arrays.sort(entries, new Comparator<Map.Entry<Integer, Integer>>() {
			@Override
			public int compare(Map.Entry<Integer, Integer> entry, Map.Entry<Integer, Integer> entry1)
			{
				return entry.getValue().equals(entry1.getValue()) ? 0 : entry.getValue() < entry1.getValue() ? 1 : -1;
			}
		});
		
		Map<Integer, Integer> ranksByOwnerId = new HashMap<Integer, Integer>();
		int ranking = 1;
		for (Map.Entry<Integer, Integer> entry : entries)
			ranksByOwnerId.put(entry.getKey(), ranking++);
		
		return ranksByOwnerId;
	}
}
