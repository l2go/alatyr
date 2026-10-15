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
package com.l2jfree.gameserver.model.entity.hellbound;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.hellbound.TowerOfNaiaManager;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.world.spawn.SpawnData;

/**
 * @author hex1r0
 */
public final class TowerOfNaiaRoom
{
	private static Logger _log = LoggerFactory.getLogger(TowerOfNaiaRoom.class);
	
	private int[] _preOpenDoorIds = null;
	private int[] _preCloseDoorIds = null;
	private int[] _postOpenDoorIds = null;
	private int[] _postCloseDoorIds = null;
	
	private List<SpawnData> _mobSpawnData = new ArrayList<SpawnData>();
	private SpawnData _ingeniousContraptionSpawnData = null;
	
	public void init(final int roomId)
	{
		initDoors(roomId);
		initSpawns(roomId);
	}
	
	public void initDoors(final int roomId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT door_id, door_action FROM naia_room_door WHERE room_number = ?");
			statement.setInt(1, roomId);
			ResultSet rset = statement.executeQuery();
			while (rset.next())
			{
				int doorId = rset.getInt("door_id");
				String action = rset.getString("door_action");
				switch (action)
				{
					case "PRE_OPEN":
						_preOpenDoorIds = ArrayUtils.add(_preOpenDoorIds, doorId);
						break;
					case "PRE_CLOSE":
						_preCloseDoorIds = ArrayUtils.add(_preCloseDoorIds, doorId);
						break;
					case "POST_OPEN":
						_postOpenDoorIds = ArrayUtils.add(_postOpenDoorIds, doorId);
						break;
					case "POST_CLOSE":
						_postCloseDoorIds = ArrayUtils.add(_postCloseDoorIds, doorId);
						break;
				}
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("TowerOfNaia: Door could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void initSpawns(final int roomId)
	{
		_mobSpawnData.clear();
		_ingeniousContraptionSpawnData = null;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT npc_template_id, x, y, z, heading, respawn_delay_s FROM naia_room_spawn WHERE room_number = ?");
			statement.setInt(1, roomId);
			ResultSet rset = statement.executeQuery();
			while (rset.next())
			{
				int npcId = rset.getInt("npc_template_id");
				L2NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(npcId);
				if (npcTemplate != null)
				{
					int x = rset.getInt("x");
					int y = rset.getInt("y");
					int z = rset.getInt("z");
					int heading = rset.getInt("heading");
					// NULL means the NPC does not respawn, which SpawnData takes as a negative delay
					int respawnDelay = rset.getInt("respawn_delay_s");
					if (rset.wasNull())
						respawnDelay = -1;
					SpawnData spawnData = new SpawnData(npcId, x, y, z, heading, respawnDelay);
					if (npcId == TowerOfNaiaManager.ROOM_CONTROLLER_ID)
						_ingeniousContraptionSpawnData = spawnData;
					else
						_mobSpawnData.add(spawnData);
				}
				else
				{
					_log.warn("TowerOfNaia: Data missing in NPC table for ID: " + npcId + ".");
				}
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("TowerOfNaia: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void prepare(final int instanceId)
	{
		DoorTable.openInstanceDoors(instanceId, _preOpenDoorIds);
		if (_ingeniousContraptionSpawnData != null)
		{
			spawnNpc(_ingeniousContraptionSpawnData, instanceId);
		}
	}
	
	public void start(final int instanceId)
	{
		DoorTable.closeInstanceDoors(instanceId, _preCloseDoorIds);
		for (SpawnData mob : _mobSpawnData)
			spawnNpc(mob, instanceId);
	}
	
	public void finish(final int instanceId)
	{
		DoorTable.openInstanceDoors(instanceId, _postOpenDoorIds);
		DoorTable.closeInstanceDoors(instanceId, _postCloseDoorIds);
	}
	
	private L2Npc spawnNpc(final SpawnData spawnData, final int instanceId)
	{
		L2Spawn spawn = new L2Spawn(spawnData.npcId);
		spawn.setLocx(spawnData.x);
		spawn.setLocy(spawnData.y);
		spawn.setLocz(spawnData.z);
		spawn.setHeading(spawnData.heading);
		if (spawnData.respawnDelay < 0)
		{
			spawn.stopRespawn();
		}
		else
		{
			spawn.setRespawnDelay(spawnData.respawnDelay);
			spawn.startRespawn();
		}
		spawn.setInstanceId(instanceId);
		
		return spawn.doSpawn();
	}
}
