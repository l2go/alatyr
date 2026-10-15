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
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;

/**
 * The correct name would be <I>SiegeGuardSpawnManager</I>.
 * It manages the guard spawn list(s) and spawning/unspawning
 * guards during siege.
 * @author unknown & savormix
 */
public class SiegeGuardManager
{
	private static final Logger _log = LoggerFactory.getLogger(SiegeGuardManager.class);
	private static final int DEFAULT_GUARD_RESPAWN = 600; // as earlier
	private static final String LOAD_NPC_GUARDS =
			"SELECT id, npc_template_id, x, y, z, heading, respawn_delay_s FROM castle_siege_guard WHERE castle_id=? ORDER BY id";
	private static final String ADD_NPC_GUARD =
			"INSERT INTO castle_siege_guard (castle_id, npc_template_id, x, y, z, heading, respawn_delay_s) VALUES (?,?,?,?,?,?,?)";
	
	private final Castle _castle;
	private final List<L2Spawn> _siegeGuardSpawn;
	private volatile int _spawnId;
	
	public SiegeGuardManager(Castle castle)
	{
		_castle = castle;
		_siegeGuardSpawn = new ArrayList<L2Spawn>();
		_spawnId = Integer.MIN_VALUE;
	}
	
	/**
	 * Spawns siege guards only if {@link Config#ALT_SPAWN_SIEGE_GUARD} is true.<BR>
	 * If the castle is owned by NPCs, loads & spawns guards defined in
	 * <code>castle_siege_guard</code>, since it was always replaceable (and mercenary
	 * positions were always lost after updating).<BR>
	 * If the castle is owned by a clan, spawns guards defined in
	 * <code>castle_hired_guard</code>, which contains only mercenary positions.
	 * <BR><BR>
	 * If siege guards are not spawned, mercenary positions are retained, since they
	 * were not used (they cost quite much to hire).
	 */
	public void spawnSiegeGuard()
	{
		if (!Config.ALT_SPAWN_SIEGE_GUARD)
			return;
		try
		{
			loadSiegeGuard();
			for (L2Spawn spawn : getSiegeGuardSpawn())
				if (spawn != null)
					spawn.init();
		}
		catch (RuntimeException e)
		{
			_log.warn("Error spawning siege guards for castle " + getCastle().getName() + ":", e);
		}
	}
	
	/**
	 * Unspawns siege guards and clears the {@link #_siegeGuardSpawn}
	 * <U>Should only be called after siege.</U>
	 */
	public void unspawnSiegeGuard()
	{
		for (L2Spawn spawn : getSiegeGuardSpawn())
		{
			if (spawn == null)
				continue;
			
			spawn.stopRespawn();
			if (spawn.getLastSpawn() != null)
				spawn.getLastSpawn().doDie(spawn.getLastSpawn());
		}
		getSiegeGuardSpawn().clear();
	}
	
	/**
	 * Add a mercenary spawn to the guard list.<BR>
	 * <B><U>Not to be called from {@link MercTicketManager#addPosition(L2Player)}
	 * or any subsequent methods!</U></B><BR>
	 * This method is used to build the spawn list just before spawning the
	 * siege guards in siege (think of it as an alternative to
	 * {@link #loadSiegeGuard()}). That is also the reason we do not have any methods
	 * to remove mercenary spawns and that's why we store positions in MercTicketManager.
	 * @param npc Mercenary NPC ID
	 * @param x Mercenary position's coordinate X
	 * @param y Mercenary position's coordinate Y
	 * @param z Mercenary position's coordinate Z
	 * @param heading Mercenary position's heading
	 */
	public final void addMercenary(int npc, int x, int y, int z, int heading)
	{
		L2NpcTemplate temp = NpcTable.getInstance().getTemplate(npc);
		if (temp != null)
		{
			L2Spawn s = new L2Spawn(temp);
			s.setId(_spawnId++);
			s.setAmount(1);
			s.setLocx(x);
			s.setLocy(y);
			s.setLocz(z);
			s.setHeading(heading);
			s.setRespawnDelay(0);
			s.setLocation(0);
			_siegeGuardSpawn.add(s);
		}
		else
			_log.warn("Missing mercenary NPC data: " + npc);
	}
	
	/**
	 * Saves a castle guard directly to the database.<BR>
	 * There are no restrictions for the NPC, however, it will be saved
	 * as a NPC guard (not a mercenary).
	 * @param gm Game Master (player)
	 * @param npc any NPC's ID
	 */
	public final void addAnyGuard(L2Player gm, int npc, int respawn)
	{
		Castle c = CastleManager.getInstance().getCastle(gm);
		if (c == null)
		{
			gm.sendMessage("You must be on a castle's ground.");
			return;
		}
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement ps = con.prepareStatement(ADD_NPC_GUARD);
			ps.setInt(1, c.getCastleId());
			ps.setInt(2, npc);
			ps.setInt(3, gm.getX());
			ps.setInt(4, gm.getY());
			ps.setInt(5, gm.getZ());
			ps.setInt(6, gm.getHeading());
			ps.setInt(7, respawn);
			ps.executeUpdate();
			ps.close();
		}
		catch (Exception e)
		{
			_log.warn("Error adding siege guard for castle " + getCastle().getName(), e);
			gm.sendMessage("Failed! Reason: " + e.getMessage());
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * This method behaves just as
	 * <CODE>addAnyGuard(gm, npc, {@link #DEFAULT_GUARD_RESPAWN})</CODE>
	 * @param gm Game Master (player)
	 * @param npc any NPC's ID
	 * @see #addAnyGuard(L2Player, int, int)
	 */
	public final void addAnyGuard(L2Player gm, int npc)
	{
		addAnyGuard(gm, npc, DEFAULT_GUARD_RESPAWN);
	}
	
	/**
	 * Load guards defined in <CODE>castle_siege_guard</CODE> if castle is
	 * owned by NPCs.<BR>
	 * Calls {@link MercTicketManager#buildSpawns(SiegeGuardManager)} if
	 * castle is owned by a player clan.
	 */
	private void loadSiegeGuard()
	{
		if (getCastle().getOwnerId() > 0)
		{
			MercTicketManager.getInstance().buildSpawns(this);
			return;
		}
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(LOAD_NPC_GUARDS);
			statement.setInt(1, getCastle().getCastleId());
			ResultSet rs = statement.executeQuery();
			
			L2Spawn spawn1;
			L2NpcTemplate template1;
			
			while (rs.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rs.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawn1 = new L2Spawn(template1);
					spawn1.setId(rs.getInt("id"));
					spawn1.setAmount(1);
					spawn1.setLocx(rs.getInt("x"));
					spawn1.setLocy(rs.getInt("y"));
					spawn1.setLocz(rs.getInt("z"));
					spawn1.setHeading(rs.getInt("heading"));
					spawn1.setRespawnDelay(rs.getInt("respawn_delay_s"));
					spawn1.setLocation(0);
					_siegeGuardSpawn.add(spawn1);
				}
				else
					_log.warn("Missing npc data in npc table for id: " + rs.getInt("npc_template_id"));
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Error loading siege guard for castle " + getCastle().getName(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/** @return the owner castle */
	public final Castle getCastle()
	{
		return _castle;
	}
	
	/** @return guard spawn list */
	public final List<L2Spawn> getSiegeGuardSpawn()
	{
		return _siegeGuardSpawn;
	}
}
