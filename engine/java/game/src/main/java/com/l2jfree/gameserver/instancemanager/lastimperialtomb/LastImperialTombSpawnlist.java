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
package com.l2jfree.gameserver.instancemanager.lastimperialtomb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;

/**
*
* @author  L2J_JP SANDMAN
*/

public class LastImperialTombSpawnlist
{
	private final static Logger _log = LoggerFactory.getLogger(LastImperialTombSpawnlist.class);
	
	private static List<L2Spawn> _Room1SpawnList1st = new ArrayList<L2Spawn>();
	private static List<L2Spawn> _Room1SpawnList2nd = new ArrayList<L2Spawn>();
	private static List<L2Spawn> _Room1SpawnList3rd = new ArrayList<L2Spawn>();
	private static List<L2Spawn> _Room1SpawnList4th = new ArrayList<L2Spawn>();
	private static List<L2Spawn> _Room2InsideSpawnList = new ArrayList<L2Spawn>();
	private static List<L2Spawn> _Room2OutsideSpawnList = new ArrayList<L2Spawn>();
	
	private LastImperialTombSpawnlist()
	{
	}
	
	public static LastImperialTombSpawnlist getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public void fill()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT id, npc_template_id, npc_count, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'LAST_IMPERIAL_TOMB' ORDER BY id");
			ResultSet rset = statement.executeQuery();
			
			int npcTemplateId;
			L2Spawn spawnDat;
			L2NpcTemplate npcTemplate;
			
			while (rset.next())
			{
				npcTemplateId = rset.getInt("npc_template_id");
				npcTemplate = NpcTable.getInstance().getTemplate(npcTemplateId);
				
				if (npcTemplate != null)
				{
					spawnDat = new L2Spawn(npcTemplate);
					spawnDat.setId(rset.getInt("id"));
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					
					switch (npcTemplateId)
					{
						case 18328:
						case 18330:
						case 18332:
							_Room1SpawnList1st.add(spawnDat);
							break;
						
						case 18329:
							_Room1SpawnList2nd.add(spawnDat);
							break;
						
						case 18333:
							_Room1SpawnList3rd.add(spawnDat);
							break;
						
						case 18331:
							_Room1SpawnList4th.add(spawnDat);
							break;
						
						case 18339:
							_Room2InsideSpawnList.add(spawnDat);
							break;
						
						case 18334:
						case 18335:
						case 18336:
						case 18337:
						case 18338:
							_Room2OutsideSpawnList.add(spawnDat);
							break;
					}
				}
				else
				{
					_log.warn("LastImperialTombSpawnlist: Data missing in NPC table for ID: " + npcTemplateId + ".");
				}
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("LastImperialTombSpawnlist: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room1SpawnList1st.size() + " Room1 1st Npc Spawn Locations.");
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room1SpawnList2nd.size() + " Room1 2nd Npc Spawn Locations.");
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room1SpawnList3rd.size() + " Room1 3rd Npc Spawn Locations.");
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room1SpawnList4th.size() + " Room1 4th Npc Spawn Locations.");
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room2InsideSpawnList.size()
				+ " Room2 Inside Npc Spawn Locations.");
		_log.info("LastImperialTombSpawnlist: Loaded " + _Room2OutsideSpawnList.size()
				+ " Room2 Outside Npc Spawn Locations.");
	}
	
	public void clear()
	{
		_Room1SpawnList1st.clear();
		_Room1SpawnList2nd.clear();
		_Room1SpawnList3rd.clear();
		_Room1SpawnList4th.clear();
		_Room2InsideSpawnList.clear();
		_Room2OutsideSpawnList.clear();
	}
	
	public List<L2Spawn> getRoom1SpawnList1st()
	{
		return _Room1SpawnList1st;
	}
	
	public List<L2Spawn> getRoom1SpawnList2nd()
	{
		return _Room1SpawnList2nd;
	}
	
	public List<L2Spawn> getRoom1SpawnList3rd()
	{
		return _Room1SpawnList3rd;
	}
	
	public List<L2Spawn> getRoom1SpawnList4th()
	{
		return _Room1SpawnList4th;
	}
	
	public List<L2Spawn> getRoom2InsideSpawnList()
	{
		return _Room2InsideSpawnList;
	}
	
	public List<L2Spawn> getRoom2OutsideSpawnList()
	{
		return _Room2OutsideSpawnList;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final LastImperialTombSpawnlist _instance = new LastImperialTombSpawnlist();
	}
}
