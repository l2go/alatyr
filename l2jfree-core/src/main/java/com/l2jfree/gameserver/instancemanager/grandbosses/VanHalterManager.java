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
package com.l2jfree.gameserver.instancemanager.grandbosses;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2RaidBossInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.tools.random.Rnd;

/**
 * This class ...
 * control for sequence of fight against "High Priestess van Halter".
 @version $Revision: $ $Date: $
 @author L2J_JP SANDMAN
**/

public class VanHalterManager extends BossLair
{
	private static final class SingletonHolder
	{
		private static final VanHalterManager INSTANCE = new VanHalterManager();
	}
	
	public static VanHalterManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	// List of intruders.
	protected Map<Integer, List<L2Player>> _bleedingPlayers = new LinkedHashMap<Integer, List<L2Player>>();
	
	// Spawn data of monsters.
	protected Map<Integer, L2Spawn> _monsterSpawn = new LinkedHashMap<Integer, L2Spawn>();
	protected List<L2Spawn> _royalGuardSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Spawn> _royalGuardCaptainSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Spawn> _royalGuardHelperSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Spawn> _triolRevelationSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Spawn> _triolRevelationAlive = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Spawn> _guardOfAltarSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected Map<Integer, L2Spawn> _cameraMarkerSpawn = new LinkedHashMap<Integer, L2Spawn>();
	protected L2Spawn _ritualOfferingSpawn = null;
	protected L2Spawn _ritualSacrificeSpawn = null;
	protected L2Spawn _vanHalterSpawn = null;
	
	// Instance of monsters.
	protected List<L2Npc> _monsters = new CopyOnWriteArrayList<L2Npc>();
	protected List<L2Npc> _royalGuard = new CopyOnWriteArrayList<L2Npc>();
	protected List<L2Npc> _royalGuardCaptain = new CopyOnWriteArrayList<L2Npc>();
	protected List<L2Npc> _royalGuardHepler = new CopyOnWriteArrayList<L2Npc>();
	protected List<L2Npc> _triolRevelation = new CopyOnWriteArrayList<L2Npc>();
	protected List<L2Npc> _guardOfAltar = new CopyOnWriteArrayList<L2Npc>();
	protected Map<Integer, L2Npc> _cameraMarker = new LinkedHashMap<Integer, L2Npc>();
	protected List<L2DoorInstance> _doorOfAltar = new CopyOnWriteArrayList<L2DoorInstance>();
	protected List<L2DoorInstance> _doorOfSacrifice = new CopyOnWriteArrayList<L2DoorInstance>();
	protected L2Npc _ritualOffering = null;
	protected L2Npc _ritualSacrifice = null;
	protected L2RaidBossInstance _vanHalter = null;
	
	// Task
	protected ScheduledFuture<?> _movieTask = null;
	protected ScheduledFuture<?> _closeDoorOfAltarTask = null;
	protected ScheduledFuture<?> _openDoorOfAltarTask = null;
	protected ScheduledFuture<?> _lockUpDoorOfAltarTask = null;
	protected ScheduledFuture<?> _callRoyalGuardHelperTask = null;
	protected ScheduledFuture<?> _timeUpTask = null;
	protected ScheduledFuture<?> _intervalTask = null;
	protected ScheduledFuture<?> _halterEscapeTask = null;
	protected ScheduledFuture<?> _setBleedTask = null;
	
	// State of High Priestess van Halter
	boolean _isLocked = false;
	boolean _isHalterSpawned = false;
	boolean _isSacrificeSpawned = false;
	boolean _isCaptainSpawned = false;
	boolean _isHelperCalled = false;
	
	public VanHalterManager()
	{
		_questName = null;
		_state = new GrandBossState(29062);
	}
	
	@Override
	public void setUnspawn()
	{
	}
	
	// Initialize
	@Override
	public void init()
	{
		// Clear flag.
		_isLocked = false;
		_isCaptainSpawned = false;
		_isHelperCalled = false;
		_isHalterSpawned = false;
		
		// Setting door state.
		_doorOfAltar.add(DoorTable.getInstance().getDoor(19160014));
		_doorOfAltar.add(DoorTable.getInstance().getDoor(19160015));
		openDoorOfAltar(true);
		_doorOfSacrifice.add(DoorTable.getInstance().getDoor(19160016));
		_doorOfSacrifice.add(DoorTable.getInstance().getDoor(19160017));
		closeDoorOfSacrifice();
		
		// Load spawn data of monsters.
		loadRoyalGuard();
		loadTriolRevelation();
		loadRoyalGuardCaptain();
		loadRoyalGuardHelper();
		loadGuardOfAltar();
		loadVanHalter();
		loadRitualOffering();
		loadRitualSacrifice();
		
		// Spawn monsters.
		spawnRoyalGuard();
		spawnTriolRevelation();
		spawnVanHalter();
		spawnRitualOffering();
		
		// Setting spawn data of Dummy camera marker.
		_cameraMarkerSpawn.clear();
		try
		{
			L2NpcTemplate template1 = NpcTable.getInstance().getTemplate(13014); // Dummy npc
			L2Spawn tempSpawn;
			
			// Dummy camera marker.
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(-16397);
			tempSpawn.setLocy(-55200);
			tempSpawn.setLocz(-10449);
			tempSpawn.setHeading(16384);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(60000);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_cameraMarkerSpawn.put(1, tempSpawn);
			
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(-16397);
			tempSpawn.setLocy(-55200);
			tempSpawn.setLocz(-10051);
			tempSpawn.setHeading(16384);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(60000);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_cameraMarkerSpawn.put(2, tempSpawn);
			
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(-16397);
			tempSpawn.setLocy(-55200);
			tempSpawn.setLocz(-9741);
			tempSpawn.setHeading(16384);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(60000);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_cameraMarkerSpawn.put(3, tempSpawn);
			
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(-16397);
			tempSpawn.setLocy(-55200);
			tempSpawn.setLocz(-9394);
			tempSpawn.setHeading(16384);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(60000);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_cameraMarkerSpawn.put(4, tempSpawn);
			
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(-16397);
			tempSpawn.setLocy(-55197);
			tempSpawn.setLocz(-8739);
			tempSpawn.setHeading(16384);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(60000);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_cameraMarkerSpawn.put(5, tempSpawn);
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		// Set time up.
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = ThreadPoolManager.getInstance().scheduleGeneral(new TimeUp(), Config.HPH_ACTIVITYTIMEOFHALTER);
		
		// Set bleeding to players.
		if (_setBleedTask != null)
			_setBleedTask.cancel(false);
		_setBleedTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new Bleeding(), 2000, 2000);
		
		// Check state of High Priestess van Halter.
		_log.info("VanHalterManager : State of High Priestess van Halter is " + _state.getState() + ".");
		if (_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
			enterInterval();
		else
			_state.setState(GrandBossState.StateEnum.NOTSPAWN);
		
		Date dt = new Date(_state.getRespawnDate());
		_log.info("VanHalterManager : Next spawn date of High Priestess van Halter is " + dt + ".");
		_log.info("VanHalterManager : init VanHalterManager.");
	}
	
	// Load Royal Guard.
	protected void loadRoyalGuard()
	{
		_royalGuardSpawn.clear();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id BETWEEN ? AND ? ORDER BY id");
			statement.setInt(1, 22175);
			statement.setInt(2, 22176);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_royalGuardSpawn.add(spawnDat);
				}
				else
				{
					_log.warn("VanHalterManager.loadRoyalGuard: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadRoyalGuard: Loaded " + _royalGuardSpawn.size()
					+ " Royal Guard spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadRoyalGuard: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnRoyalGuard()
	{
		if (!_royalGuard.isEmpty())
			deleteRoyalGuard();
		
		for (L2Spawn rgs : _royalGuardSpawn)
		{
			rgs.startRespawn();
			_royalGuard.add(rgs.doSpawn());
		}
	}
	
	protected void deleteRoyalGuard()
	{
		for (L2Npc rg : _royalGuard)
		{
			rg.getSpawn().stopRespawn();
			rg.deleteMe();
		}
		
		_royalGuard.clear();
	}
	
	// Load Triol's Revelation.
	protected void loadTriolRevelation()
	{
		_triolRevelationSpawn.clear();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id BETWEEN ? AND ? ORDER BY id");
			statement.setInt(1, 32058);
			statement.setInt(2, 32068);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_triolRevelationSpawn.add(spawnDat);
				}
				else
				{
					_log.warn("VanHalterManager.loadTriolRevelation: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadTriolRevelation: Loaded " + _triolRevelationSpawn.size()
					+ " Triol's Revelation spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadTriolRevelation: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnTriolRevelation()
	{
		if (!_triolRevelation.isEmpty())
			deleteTriolRevelation();
		
		for (L2Spawn trs : _triolRevelationSpawn)
		{
			trs.startRespawn();
			_triolRevelation.add(trs.doSpawn());
			if (trs.getNpcid() != 32067 && trs.getNpcid() != 32068)
				_triolRevelationAlive.add(trs);
		}
	}
	
	protected void deleteTriolRevelation()
	{
		for (L2Npc tr : _triolRevelation)
		{
			tr.getSpawn().stopRespawn();
			tr.deleteMe();
		}
		_triolRevelation.clear();
		_bleedingPlayers.clear();
	}
	
	// Load Royal Guard Captain.
	protected void loadRoyalGuardCaptain()
	{
		_royalGuardCaptainSpawn.clear();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 22188);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_royalGuardCaptainSpawn.add(spawnDat);
				}
				else
				{
					_log.warn("VanHalterManager.loadRoyalGuardCaptain: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadRoyalGuardCaptain: Loaded " + _royalGuardCaptainSpawn.size()
					+ " Royal Guard Captain spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadRoyalGuardCaptain: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnRoyalGuardCaptain()
	{
		if (!_royalGuardCaptain.isEmpty())
			deleteRoyalGuardCaptain();
		
		for (L2Spawn trs : _royalGuardCaptainSpawn)
		{
			trs.startRespawn();
			_royalGuardCaptain.add(trs.doSpawn());
		}
		_isCaptainSpawned = true;
	}
	
	protected void deleteRoyalGuardCaptain()
	{
		for (L2Npc tr : _royalGuardCaptain)
		{
			tr.getSpawn().stopRespawn();
			tr.deleteMe();
		}
		
		_royalGuardCaptain.clear();
	}
	
	// Load Royal Guard Helper.
	protected void loadRoyalGuardHelper()
	{
		_royalGuardHelperSpawn.clear();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 22191);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_royalGuardHelperSpawn.add(spawnDat);
				}
				else
				{
					_log.warn("VanHalterManager.loadRoyalGuardHelper: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadRoyalGuardHelper: Loaded " + _royalGuardHelperSpawn.size()
					+ " Royal Guard Helper spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadRoyalGuardHelper: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnRoyalGuardHepler()
	{
		for (L2Spawn trs : _royalGuardHelperSpawn)
		{
			trs.startRespawn();
			_royalGuardHepler.add(trs.doSpawn());
		}
	}
	
	protected void deleteRoyalGuardHepler()
	{
		for (L2Npc tr : _royalGuardHepler)
		{
			tr.getSpawn().stopRespawn();
			tr.deleteMe();
		}
		_royalGuardHepler.clear();
	}
	
	// Load Guard Of Altar
	protected void loadGuardOfAltar()
	{
		_guardOfAltarSpawn.clear();
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 32051);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_guardOfAltarSpawn.add(spawnDat);
				}
				else
				{
					_log.warn("VanHalterManager.loadGuardOfAltar: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadGuardOfAltar: Loaded " + _guardOfAltarSpawn.size()
					+ " Guard Of Altar spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadGuardOfAltar: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnGuardOfAltar()
	{
		if (!_guardOfAltar.isEmpty())
			deleteGuardOfAltar();
		
		for (L2Spawn trs : _guardOfAltarSpawn)
		{
			trs.startRespawn();
			_guardOfAltar.add(trs.doSpawn());
		}
	}
	
	protected void deleteGuardOfAltar()
	{
		for (L2Npc tr : _guardOfAltar)
		{
			tr.getSpawn().stopRespawn();
			tr.deleteMe();
		}
		
		_guardOfAltar.clear();
	}
	
	// Load High Priestess van Halter.
	protected void loadVanHalter()
	{
		_vanHalterSpawn = null;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 29062);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_vanHalterSpawn = spawnDat;
				}
				else
				{
					_log.warn("VanHalterManager.loadVanHalter: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadVanHalter: Loaded High Priestess van Halter spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadVanHalter: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnVanHalter()
	{
		if (_vanHalterSpawn == null)
			return;
		_vanHalter = (L2RaidBossInstance)_vanHalterSpawn.doSpawn();
		_vanHalter.setIsImmobilized(true);
		_vanHalter.setIsInvul(true);
		_isHalterSpawned = true;
	}
	
	protected void deleteVanHalter()
	{
		_vanHalter.setIsImmobilized(false);
		_vanHalter.setIsInvul(false);
		_vanHalter.getSpawn().stopRespawn();
		_vanHalter.deleteMe();
	}
	
	// Load Ritual Offering.
	protected void loadRitualOffering()
	{
		_ritualOfferingSpawn = null;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 32038);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_ritualOfferingSpawn = spawnDat;
				}
				else
				{
					_log.warn("VanHalterManager.loadRitualOffering: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadRitualOffering: Loaded Ritual Offering spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadRitualOffering: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnRitualOffering()
	{
		if (_ritualOfferingSpawn == null)
			return;
		_ritualOffering = _ritualOfferingSpawn.doSpawn();
		_ritualOffering.setIsImmobilized(true);
		_ritualOffering.setIsInvul(true);
		_ritualOffering.startParalyze();
	}
	
	protected void deleteRitualOffering()
	{
		_ritualOffering.setIsImmobilized(false);
		_ritualOffering.setIsInvul(false);
		_ritualOffering.stopParalyze(false);
		_ritualOffering.getSpawn().stopRespawn();
		_ritualOffering.deleteMe();
	}
	
	// Load Ritual Sacrifice.
	protected void loadRitualSacrifice()
	{
		_ritualSacrificeSpawn = null;
		
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(null);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s FROM spawn WHERE spawn_group = 'VAN_HALTER' AND npc_template_id = ? ORDER BY id");
			statement.setInt(1, 22195);
			ResultSet rset = statement.executeQuery();
			
			L2Spawn spawnDat;
			L2NpcTemplate template1;
			
			while (rset.next())
			{
				template1 = NpcTable.getInstance().getTemplate(rset.getInt("npc_template_id"));
				if (template1 != null)
				{
					spawnDat = new L2Spawn(template1);
					spawnDat.setAmount(rset.getInt("npc_count"));
					spawnDat.setLocx(rset.getInt("x"));
					spawnDat.setLocy(rset.getInt("y"));
					spawnDat.setLocz(rset.getInt("z"));
					spawnDat.setHeading(rset.getInt("heading"));
					spawnDat.setRespawnDelay(rset.getInt("respawn_delay_s"));
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_ritualSacrificeSpawn = spawnDat;
				}
				else
				{
					_log.warn("VanHalterManager.loadRitualSacrifice: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("VanHalterManager.loadRitualSacrifice: Loaded Ritual Sacrifice spawn locations.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.warn("VanHalterManager.loadRitualSacrifice: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void spawnRitualSacrifice()
	{
		_ritualSacrifice = _ritualSacrificeSpawn.doSpawn();
		_ritualSacrifice.setIsImmobilized(true);
		_ritualSacrifice.setIsInvul(true);
		_isSacrificeSpawned = true;
	}
	
	protected void deleteRitualSacrifice()
	{
		if (!_isSacrificeSpawned)
			return;
		
		_ritualSacrifice.getSpawn().stopRespawn();
		_ritualSacrifice.deleteMe();
		_isSacrificeSpawned = false;
	}
	
	protected void spawnCameraMarker()
	{
		_cameraMarker.clear();
		for (int i = 1; i <= _cameraMarkerSpawn.size(); i++)
		{
			_cameraMarker.put(i, _cameraMarkerSpawn.get(i).doSpawn());
			_cameraMarker.get(i).getSpawn().stopRespawn();
			_cameraMarker.get(i).setIsImmobilized(true);
		}
	}
	
	protected void deleteCameraMarker()
	{
		if (_cameraMarker.isEmpty())
			return;
		
		for (int i = 1; i <= _cameraMarker.size(); i++)
		{
			_cameraMarker.get(i).deleteMe();
		}
		_cameraMarker.clear();
	}
	
	// Door control.
	/**
	 * @param intruder
	 */
	public void intruderDetection(L2Player intruder)
	{
		if (_lockUpDoorOfAltarTask == null && !_isLocked && _isCaptainSpawned)
		{
			_lockUpDoorOfAltarTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new LockUpDoorOfAltar(),
							Config.HPH_TIMEOFLOCKUPDOOROFALTAR);
		}
	}
	
	private class LockUpDoorOfAltar implements Runnable
	{
		@Override
		public void run()
		{
			closeDoorOfAltar(false);
			_isLocked = true;
			_lockUpDoorOfAltarTask = null;
		}
	}
	
	protected void openDoorOfAltar(boolean loop)
	{
		for (L2DoorInstance door : _doorOfAltar)
		{
			if (door == null)
				continue;
			try
			{
				door.openMe();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		
		if (loop)
		{
			_isLocked = false;
			
			if (_closeDoorOfAltarTask != null)
				_closeDoorOfAltarTask.cancel(false);
			_closeDoorOfAltarTask = null;
			_closeDoorOfAltarTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new CloseDoorOfAltar(),
							Config.HPH_INTERVALOFDOOROFALTER);
		}
		else
		{
			if (_closeDoorOfAltarTask != null)
				_closeDoorOfAltarTask.cancel(false);
			_closeDoorOfAltarTask = null;
		}
	}
	
	private class OpenDoorOfAltar implements Runnable
	{
		@Override
		public void run()
		{
			openDoorOfAltar(true);
		}
	}
	
	protected void closeDoorOfAltar(boolean loop)
	{
		for (L2DoorInstance door : _doorOfAltar)
		{
			door.closeMe();
		}
		
		if (loop)
		{
			if (_openDoorOfAltarTask != null)
				_openDoorOfAltarTask.cancel(false);
			_openDoorOfAltarTask = null;
			_openDoorOfAltarTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new OpenDoorOfAltar(),
							Config.HPH_INTERVALOFDOOROFALTER);
		}
		else
		{
			if (_openDoorOfAltarTask != null)
				_openDoorOfAltarTask.cancel(false);
			_openDoorOfAltarTask = null;
		}
	}
	
	private class CloseDoorOfAltar implements Runnable
	{
		@Override
		public void run()
		{
			closeDoorOfAltar(true);
		}
	}
	
	protected void openDoorOfSacrifice()
	{
		for (L2DoorInstance door : _doorOfSacrifice)
		{
			try
			{
				door.openMe();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	protected void closeDoorOfSacrifice()
	{
		for (L2DoorInstance door : _doorOfSacrifice)
		{
			if (door == null)
				continue;
			try
			{
				door.closeMe();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	// event
	public void checkTriolRevelationDestroy()
	{
		if (_isCaptainSpawned)
			return;
		
		boolean isTriolRevelationDestroyed = true;
		for (L2Spawn tra : _triolRevelationAlive)
		{
			if (!tra.getLastSpawn().isDead())
				isTriolRevelationDestroyed = false;
		}
		
		if (isTriolRevelationDestroyed)
		{
			spawnRoyalGuardCaptain();
		}
	}
	
	public void checkRoyalGuardCaptainDestroy()
	{
		if (!_isHalterSpawned)
			return;
		
		deleteRoyalGuard();
		deleteRoyalGuardCaptain();
		spawnGuardOfAltar();
		openDoorOfSacrifice();
		
		CreatureSay cs =
				new CreatureSay(0, SystemChatChannelId.Chat_Alliance, "Altar's Gatekeeper",
						"The door of the 3rd floor in the altar was opened.");
		for (L2Player pc : getPlayersInside())
		{
			pc.sendPacket(cs);
		}
		
		_vanHalter.setIsImmobilized(true);
		_vanHalter.setIsInvul(true);
		spawnCameraMarker();
		
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = null;
		
		_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(1), Config.HPH_APPTIMEOFHALTER);
	}
	
	// Start fight against High Priestess van Halter.
	protected void combatBeginning()
	{
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = ThreadPoolManager.getInstance().scheduleGeneral(new TimeUp(), Config.HPH_FIGHTTIMEOFHALTER);
		
		Map<Integer, L2Player> _targets = new LinkedHashMap<Integer, L2Player>();
		int i = 0;
		
		for (L2Player pc : _vanHalter.getKnownList().getKnownPlayers().values())
		{
			i++;
			_targets.put(i, pc);
		}
		
		_vanHalter.reduceCurrentHp(1, _targets.get(Rnd.get(1, i)));
	}
	
	// Call Royal Guard Helper and escape from player.
	public void callRoyalGuardHelper()
	{
		if (!_isHelperCalled)
		{
			_isHelperCalled = true;
			_halterEscapeTask = ThreadPoolManager.getInstance().scheduleGeneral(new HalterEscape(), 500);
			_callRoyalGuardHelperTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new CallRoyalGuardHelper(), 1000);
		}
	}
	
	private class CallRoyalGuardHelper implements Runnable
	{
		@Override
		public void run()
		{
			spawnRoyalGuardHepler();
			
			if (_royalGuardHepler.size() <= Config.HPH_CALLROYALGUARDHELPERCOUNT && !_vanHalter.isDead())
			{
				if (_callRoyalGuardHelperTask != null)
					_callRoyalGuardHelperTask.cancel(false);
				_callRoyalGuardHelperTask =
						ThreadPoolManager.getInstance().scheduleGeneral(new CallRoyalGuardHelper(),
								Config.HPH_CALLROYALGUARDHELPERINTERVAL);
			}
			else
			{
				if (_callRoyalGuardHelperTask != null)
					_callRoyalGuardHelperTask.cancel(false);
				_callRoyalGuardHelperTask = null;
			}
		}
	}
	
	private class HalterEscape implements Runnable
	{
		@Override
		public void run()
		{
			if (_royalGuardHepler.size() <= Config.HPH_CALLROYALGUARDHELPERCOUNT && !_vanHalter.isDead())
			{
				if (_vanHalter.isAfraid())
				{
					_vanHalter.stopFear(true);
				}
				else
				{
					_vanHalter.startFear();
					if (_vanHalter.getZ() >= -10476)
					{
						L2CharPosition pos = new L2CharPosition(-16397, -53308, -10448, 0);
						if (_vanHalter.getX() == pos.x && _vanHalter.getY() == pos.y)
						{
							_vanHalter.stopFear(true);
						}
						else
						{
							_vanHalter.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, pos);
						}
					}
					else if (_vanHalter.getX() >= -16397)
					{
						L2CharPosition pos = new L2CharPosition(-15548, -54830, -10475, 0);
						_vanHalter.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, pos);
					}
					else
					{
						L2CharPosition pos = new L2CharPosition(-17248, -54830, -10475, 0);
						_vanHalter.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, pos);
					}
				}
				if (_halterEscapeTask != null)
					_halterEscapeTask.cancel(false);
				_halterEscapeTask = ThreadPoolManager.getInstance().scheduleGeneral(new HalterEscape(), 5000);
			}
			else
			{
				_vanHalter.stopFear(true);
				if (_halterEscapeTask != null)
					_halterEscapeTask.cancel(false);
				_halterEscapeTask = null;
			}
		}
	}
	
	// Check bleeding player.
	protected void addBleeding()
	{
		L2Skill bleed = SkillTable.getInstance().getInfo(4615, 12);
		
		for (L2Npc tr : _triolRevelation)
		{
			if (tr.isDead())
				continue;
			Iterable<L2Player> seen = tr.getKnownList().getKnownPlayersInRadius(tr.getAggroRange());
			if (!seen.iterator().hasNext())
				continue;
			
			ArrayList<L2Player> bpc = new ArrayList<L2Player>();
			for (L2Player pc : seen)
			{
				if (!pc.getEffects().hasEffect(bleed))
				{
					bleed.getEffects(tr, pc);
					tr.broadcastPacket(new MagicSkillUse(tr, pc, bleed.getId(), 12, bleed.getHitTime(), bleed
							.getReuseDelay()));
				}
				bpc.add(pc);
			}
			bpc.trimToSize();
			_bleedingPlayers.put(tr.getNpcId(), bpc);
		}
	}
	
	public void removeBleeding(int npcId)
	{
		List<L2Player> list = _bleedingPlayers.remove(npcId);
		if (list == null)
			return;
		for (L2Player pc : list)
			if (pc.getFirstEffect(L2EffectType.DMG_OVER_TIME) != null)
				pc.stopEffects(L2EffectType.DMG_OVER_TIME);
	}
	
	private class Bleeding implements Runnable
	{
		@Override
		public void run()
		{
			addBleeding();
		}
	}
	
	// High Priestess van Halter dead or time up.
	public void enterInterval()
	{
		// Cancel all task
		if (_callRoyalGuardHelperTask != null)
			_callRoyalGuardHelperTask.cancel(false);
		_callRoyalGuardHelperTask = null;
		
		if (_closeDoorOfAltarTask != null)
			_closeDoorOfAltarTask.cancel(false);
		_closeDoorOfAltarTask = null;
		
		if (_halterEscapeTask != null)
			_halterEscapeTask.cancel(false);
		_halterEscapeTask = null;
		
		if (_intervalTask != null)
			_intervalTask.cancel(false);
		_intervalTask = null;
		
		if (_lockUpDoorOfAltarTask != null)
			_lockUpDoorOfAltarTask.cancel(false);
		_lockUpDoorOfAltarTask = null;
		
		if (_movieTask != null)
			_movieTask.cancel(false);
		_movieTask = null;
		
		if (_openDoorOfAltarTask != null)
			_openDoorOfAltarTask.cancel(false);
		_openDoorOfAltarTask = null;
		
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = null;
		
		// Delete monsters
		if (_vanHalter.isDead())
		{
			_vanHalter.getSpawn().stopRespawn();
		}
		else
		{
			deleteVanHalter();
		}
		deleteRoyalGuardHepler();
		deleteRoyalGuardCaptain();
		deleteRoyalGuard();
		deleteRitualOffering();
		deleteRitualSacrifice();
		deleteGuardOfAltar();
		
		// Set interval end.
		if (_intervalTask != null)
			_intervalTask.cancel(false);
		
		if (!_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
		{
			int interval =
					Rnd.get(Config.HPH_FIXINTERVALOFHALTER, Config.HPH_FIXINTERVALOFHALTER
							+ Config.HPH_RANDOMINTERVALOFHALTER);
			_state.setRespawnDate(interval);
			_state.setState(GrandBossState.StateEnum.INTERVAL);
			_state.update();
		}
		
		_intervalTask = ThreadPoolManager.getInstance().scheduleGeneral(new Interval(), _state.getInterval());
	}
	
	// Interval.
	private class Interval implements Runnable
	{
		@Override
		public void run()
		{
			setupAltar();
		}
	}
	
	// Interval end.
	public void setupAltar()
	{
		// Cancel all task
		if (_callRoyalGuardHelperTask != null)
			_callRoyalGuardHelperTask.cancel(false);
		_callRoyalGuardHelperTask = null;
		
		if (_closeDoorOfAltarTask != null)
			_closeDoorOfAltarTask.cancel(false);
		_closeDoorOfAltarTask = null;
		
		if (_halterEscapeTask != null)
			_halterEscapeTask.cancel(false);
		_halterEscapeTask = null;
		
		if (_intervalTask != null)
			_intervalTask.cancel(false);
		_intervalTask = null;
		
		if (_lockUpDoorOfAltarTask != null)
			_lockUpDoorOfAltarTask.cancel(false);
		_lockUpDoorOfAltarTask = null;
		
		if (_movieTask != null)
			_movieTask.cancel(false);
		_movieTask = null;
		
		if (_openDoorOfAltarTask != null)
			_openDoorOfAltarTask.cancel(false);
		_openDoorOfAltarTask = null;
		
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = null;
		
		// Delete all monsters
		deleteVanHalter();
		deleteTriolRevelation();
		deleteRoyalGuardHepler();
		deleteRoyalGuardCaptain();
		deleteRoyalGuard();
		deleteRitualSacrifice();
		deleteRitualOffering();
		deleteGuardOfAltar();
		deleteCameraMarker();
		
		// Clear flag.
		_isLocked = false;
		_isCaptainSpawned = false;
		_isHelperCalled = false;
		_isHalterSpawned = false;
		
		// Set door state
		closeDoorOfSacrifice();
		openDoorOfAltar(true);
		
		// Respawn monsters.
		spawnTriolRevelation();
		spawnRoyalGuard();
		spawnRitualOffering();
		spawnVanHalter();
		
		_state.setState(GrandBossState.StateEnum.NOTSPAWN);
		_state.update();
		
		// Set time up.
		if (_timeUpTask != null)
			_timeUpTask.cancel(false);
		_timeUpTask = ThreadPoolManager.getInstance().scheduleGeneral(new TimeUp(), Config.HPH_ACTIVITYTIMEOFHALTER);
	}
	
	// Time up.
	private class TimeUp implements Runnable
	{
		@Override
		public void run()
		{
			enterInterval();
		}
	}
	
	// Appearance movie.
	private class Movie implements Runnable
	{
		private final int _distance = 6502500;
		private final int _taskId;
		private final List<L2Player> _players = getPlayersInside();
		
		public Movie(int taskId)
		{
			_taskId = taskId;
		}
		
		@Override
		public void run()
		{
			_vanHalter.setHeading(16384);
			_vanHalter.setTarget(_ritualOffering);
			
			switch (_taskId)
			{
				case 1:
					_state.setState(GrandBossState.StateEnum.ALIVE);
					_state.update();
					
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_vanHalter) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_vanHalter, 50, 90, 0, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(2), 16);
					
					break;
				
				case 2:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(5)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(5), 1842, 100, -3, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(3), 1);
					
					break;
				
				case 3:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(5)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(5), 1861, 97, -10, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(4), 1500);
					
					break;
				
				case 4:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(4)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(4), 1876, 97, 12, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(5), 1);
					
					break;
				
				case 5:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(4)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(4), 1839, 94, 0, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(6), 1500);
					
					break;
				
				case 6:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(3)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(3), 1872, 94, 15, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(7), 1);
					
					break;
				
				case 7:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(3)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(3), 1839, 92, 0, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(8), 1500);
					
					break;
				
				case 8:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(2)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(2), 1872, 92, 15, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(9), 1);
					
					break;
				
				case 9:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(2)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(2), 1839, 90, 5, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(10), 1500);
					
					break;
				
				case 10:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(1)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(1), 1872, 90, 5, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(11), 1);
					
					break;
				
				case 11:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_cameraMarker.get(1)) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_cameraMarker.get(1), 2002, 90, 2, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(12), 2000);
					
					break;
				
				case 12:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_vanHalter) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_vanHalter, 50, 90, 10, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(13), 1000);
					
					break;
				
				case 13:
					// High Priestess van Halter uses the skill to kill Ritual Offering.
					L2Skill skill = SkillTable.getInstance().getInfo(1168, 7);
					_ritualOffering.setIsInvul(false);
					_vanHalter.setTarget(_ritualOffering);
					_vanHalter.setIsImmobilized(false);
					_vanHalter.doCast(skill);
					_vanHalter.setIsImmobilized(true);
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(14), 4700);
					
					break;
				
				case 14:
					_ritualOffering.setIsInvul(false);
					_ritualOffering.reduceCurrentHp(_ritualOffering.getMaxHp() + 1, _vanHalter);
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(15), 4300);
					
					break;
				
				case 15:
					spawnRitualSacrifice();
					deleteRitualOffering();
					
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_vanHalter) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_vanHalter, 100, 90, 15, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(16), 2000);
					
					break;
				
				case 16:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_vanHalter) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_vanHalter, 5200, 90, -10, 9500, 6000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(17), 6000);
					
					break;
				
				case 17:
					// Reset camera.
					for (L2Player pc : _players)
					{
						pc.leaveMovieMode();
					}
					deleteRitualSacrifice();
					deleteCameraMarker();
					_vanHalter.setIsImmobilized(false);
					_vanHalter.setIsInvul(false);
					
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
					_movieTask = ThreadPoolManager.getInstance().scheduleGeneral(new Movie(18), 1000);
					
					break;
				
				case 18:
					combatBeginning();
					if (_movieTask != null)
						_movieTask.cancel(false);
					_movieTask = null;
			}
		}
	}
}
