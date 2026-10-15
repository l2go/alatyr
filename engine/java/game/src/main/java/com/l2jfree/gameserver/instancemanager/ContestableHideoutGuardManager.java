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

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;

/**
 * Alternate siege guard manager specifically for clan halls, without unnecessary clutter.
 * @author Savormix
 */
public final class ContestableHideoutGuardManager
{
	private static final Logger _log = LoggerFactory.getLogger(ContestableHideoutGuardManager.class);
	private static final String LOAD_SIEGE_GUARDS =
			"SELECT id, npc_template_id, x, y, z, heading, respawn_delay_s FROM clan_hall_siege_guard WHERE clan_hall_id=? ORDER BY id";
	private final ClanHall _hideout;
	private L2Spawn[] _guardSpawn = new L2Spawn[0];
	
	public ContestableHideoutGuardManager(ClanHall hideout)
	{
		_hideout = hideout;
		load();
	}
	
	public final void load()
	{
		Connection con = null;
		List<L2Spawn> guards = new ArrayList<L2Spawn>(50);
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement(LOAD_SIEGE_GUARDS);
			ps.setInt(1, _hideout.getId());
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				L2Spawn s = new L2Spawn(NpcTable.getInstance().getTemplate(rs.getInt("npc_template_id")));
				s.setId(rs.getInt("id"));
				s.setAmount(1);
				s.setLocx(rs.getInt("x"));
				s.setLocy(rs.getInt("y"));
				s.setLocz(rs.getInt("z"));
				s.setHeading(rs.getInt("heading"));
				s.setRespawnDelay(rs.getInt("respawn_delay_s"));
				s.setLocation(0);
				guards.add(s);
			}
			_guardSpawn = guards.toArray(new L2Spawn[guards.size()]);
		}
		catch (Exception e)
		{
			_log.error("Failed loading " + _hideout.getName() + "'s siege guards!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public final void spawnSiegeGuards()
	{
		for (L2Spawn s : _guardSpawn)
			if (s != null)
				s.init();
	}
	
	public final void despawnSiegeGuards()
	{
		for (L2Spawn s : _guardSpawn)
		{
			if (s == null)
				continue;
			s.stopRespawn();
			L2Npc guard = s.getLastSpawn();
			if (guard != null)
				guard.doDie(guard);
		}
	}
	
	public final ClanHall getContestableClanHall()
	{
		return _hideout;
	}
}
