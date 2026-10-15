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
import java.sql.Timestamp;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * @author Crion/kombat
 */
public class GrandBossSpawnManager extends BossSpawnManager
{
	/** Spawn point (catalog) and saved state (world) of every boss; without a state row the boss starts at full HP and MP. */
	private static final String SELECT_BOSSES =
			"SELECT s.npc_template_id, s.x, s.y, s.z, s.heading, s.respawn_min_delay_s, s.respawn_max_delay_s, t.respawn_at, t.current_hp, t.current_mp "
					+ "FROM grand_boss_spawn s LEFT JOIN grand_boss_state t ON t.npc_template_id = s.npc_template_id ORDER BY s.npc_template_id";
	private static final String INSERT_SPAWN =
			"INSERT INTO grand_boss_spawn (npc_template_id, x, y, z, heading) VALUES (?, ?, ?, ?, ?)";
	private static final String UPSERT_STATE =
			"INSERT INTO grand_boss_state (npc_template_id, respawn_at, current_hp, current_mp) VALUES (?, ?, ?, ?) "
					+ "ON CONFLICT (npc_template_id) DO UPDATE SET respawn_at = EXCLUDED.respawn_at, current_hp = EXCLUDED.current_hp, current_mp = EXCLUDED.current_mp";
	private static final String UPDATE_SPAWN =
			"UPDATE grand_boss_spawn SET x = ?, y = ?, z = ?, heading = ? WHERE npc_template_id = ?";
	private static final String DELETE_SPAWN = "DELETE FROM grand_boss_spawn WHERE npc_template_id = ?";
	private static final String DELETE_STATE = "DELETE FROM grand_boss_state WHERE npc_template_id = ?";

	public static GrandBossSpawnManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	protected void init()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement = con.prepareStatement(SELECT_BOSSES);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template;
			
			while (rset.next())
			{
				template = getValidTemplate(rset.getInt("npc_template_id"));
				if (template != null)
				{
					spawnDat = new L2Spawn(template);
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnMinDelay(rset.getInt("respawn_min_delay_s"));
					spawnDat.setRespawnMaxDelay(rset.getInt("respawn_max_delay_s"));
					spawnDat.setAmount(1);

					// NULL means "not waiting for a respawn" (0); no saved HP or MP means full (the status clamps it)
					Timestamp respawnAt = rset.getTimestamp("respawn_at");
					double currentHp = rset.getDouble("current_hp");
					if (rset.wasNull())
						currentHp = Double.MAX_VALUE;
					double currentMp = rset.getDouble("current_mp");
					if (rset.wasNull())
						currentMp = Double.MAX_VALUE;

					addNewSpawn(spawnDat, respawnAt == null ? 0L : respawnAt.getTime(), currentHp, currentMp, false);
				}
				else
				{
					_log.warn("GrandBossSpawnManager: Could not load grandboss #" + rset.getInt("npc_template_id")
							+ " from DB");
				}
			}
			
			_log.info("GrandBossSpawnManager: Loaded " + _bosses.size() + " Instances");
			_log.info("GrandBossSpawnManager: Scheduled " + _schedules.size() + " Instances");
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("GrandBossSpawnManager: Couldnt load grand boss data");
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@Override
	protected void insertIntoDb(L2Spawn spawnDat, long respawnTime, double currentHP, double currentMP)
	{
		// The spawn point and the state belong together: both rows or none
		WorldTransaction.run("Storing grand boss #" + spawnDat.getNpcId(), () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement = con.prepareStatement(INSERT_SPAWN);
				statement.setInt(1, spawnDat.getNpcId());
				statement.setInt(2, spawnDat.getLocx());
				statement.setInt(3, spawnDat.getLocy());
				statement.setInt(4, spawnDat.getLocz());
				statement.setInt(5, spawnDat.getHeading());
				statement.execute();
				statement.close();

				saveState(con, spawnDat.getNpcId(), respawnTime, currentHP, currentMP);
			}
			catch (SQLException e)
			{
				// Problem with storing spawn: the transaction rolls back and logs it
				throw new IllegalStateException(e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}

	private static void saveState(Connection con, int bossId, long respawnTime, double currentHP, double currentMP)
			throws SQLException
	{
		PreparedStatement statement = con.prepareStatement(UPSERT_STATE);
		statement.setInt(1, bossId);
		// 0 means the boss is alive, which is NULL in the database
		statement.setTimestamp(2, respawnTime == 0L ? null : new Timestamp(respawnTime));
		statement.setDouble(3, currentHP);
		statement.setDouble(4, currentMP);
		statement.execute();
		statement.close();
	}
	
	@Override
	public void updateSpawn(int bossId, int x, int y, int z, int h)
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement(UPDATE_SPAWN);
			statement.setInt(1, x);
			statement.setInt(2, y);
			statement.setInt(3, z);
			statement.setInt(4, h);
			statement.setInt(5, bossId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("GrandBossSpawnManager: Could not update raidboss #" + bossId + " in DB: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@Override
	protected void deleteFromDb(L2Spawn spawnDat, int bossId)
	{
		// The spawn point and the state belong together: both rows or none
		WorldTransaction.run("Removing grand boss #" + bossId, () -> {
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				PreparedStatement statement = con.prepareStatement(DELETE_SPAWN);
				statement.setInt(1, bossId);
				statement.execute();
				statement.close();

				statement = con.prepareStatement(DELETE_STATE);
				statement.setInt(1, bossId);
				statement.execute();
				statement.close();
			}
			catch (SQLException e)
			{
				// Problem with deleting spawn: the transaction rolls back and logs it
				throw new IllegalStateException(e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}
	
	@Override
	protected void updateDb()
	{
		for (Integer bossId : _storedInfo.keySet())
		{
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				L2Boss boss = _bosses.get(bossId);
				L2Spawn spawnDat = _spawns.get(bossId);
				if (boss == null || spawnDat == null)
				{
					continue;
				}
				
				if (boss.getRaidStatus().equals(StatusEnum.ALIVE))
					updateStatus(boss, false);
				
				StatsSet info = _storedInfo.get(bossId);
				if (info == null)
				{
					continue;
				}
				
				saveState(con, bossId, info.getLong("respawnTime"), info.getDouble("currentHp"),
						info.getDouble("currentMp"));
			}
			catch (SQLException e)
			{
				_log.error("GrandBossSpawnManager: Couldnt update grand_boss_state table", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	@Override
	public L2NpcTemplate getValidTemplate(int bossId)
	{
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(bossId);
		if (template == null)
			return null;
		if (!template.isAssignableTo(L2GrandBossInstance.class))
			return null;
		return template;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final GrandBossSpawnManager _instance = new GrandBossSpawnManager();
	}
}
