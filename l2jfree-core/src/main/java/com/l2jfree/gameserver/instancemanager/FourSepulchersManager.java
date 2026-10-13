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
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SepulcherMonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SepulcherNpcInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.grandbosses.BossLair;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * This class ...
 * @version $Revision: $ $Date: $
 * @author sandman
 */
public class FourSepulchersManager extends BossLair
{
	private static final String QUEST_ID = "620_FourGoblets";
	
	private static final int ENTRANCE_PASS = 7075;
	private static final int USED_PASS = 7261;
	private static final int CHAPEL_KEY = 7260;
	private static final int ANTIQUE_BROOCH = 7262;
	
	protected boolean _inEntryTime = false;
	protected boolean _inWarmUpTime = false;
	protected boolean _inAttackTime = false;
	protected boolean _inCoolDownTime = false;
	
	protected ScheduledFuture<?> _changeCoolDownTimeTask = null;
	protected ScheduledFuture<?> _changeEntryTimeTask = null;
	protected ScheduledFuture<?> _changeWarmUpTimeTask = null;
	protected ScheduledFuture<?> _changeAttackTimeTask = null;
	protected ScheduledFuture<?> _onPartyAnnihilatedTask = null;
	
	private final int[][] _startHallSpawn = { { 181632, -85587, -7218 }, { 179963, -88978, -7218 },
			{ 173217, -86132, -7218 }, { 175608, -82296, -7218 } };
	
	private final int[][][] _shadowSpawnLoc = {
			{ { 25339, 191231, -85574, -7216, 33380 }, { 25349, 189534, -88969, -7216, 32768 },
					{ 25346, 173195, -76560, -7215, 49277 }, { 25342, 175591, -72744, -7215, 49317 } },
			{ { 25342, 191231, -85574, -7216, 33380 }, { 25339, 189534, -88969, -7216, 32768 },
					{ 25349, 173195, -76560, -7215, 49277 }, { 25346, 175591, -72744, -7215, 49317 } },
			{ { 25346, 191231, -85574, -7216, 33380 }, { 25342, 189534, -88969, -7216, 32768 },
					{ 25339, 173195, -76560, -7215, 49277 }, { 25349, 175591, -72744, -7215, 49317 } },
			{ { 25349, 191231, -85574, -7216, 33380 }, { 25346, 189534, -88969, -7216, 32768 },
					{ 25342, 173195, -76560, -7215, 49277 }, { 25339, 175591, -72744, -7215, 49317 } }, };
	
	protected Map<Integer, Boolean> _archonSpawned = new LinkedHashMap<Integer, Boolean>();
	protected Map<Integer, Boolean> _hallInUse = new LinkedHashMap<Integer, Boolean>();
	protected Map<Integer, int[]> _startHallSpawns = new LinkedHashMap<Integer, int[]>();
	protected Map<Integer, Integer> _hallGateKeepers = new LinkedHashMap<Integer, Integer>();
	protected Map<Integer, Integer> _keyBoxNpc = new LinkedHashMap<Integer, Integer>();
	protected Map<Integer, Integer> _victim = new LinkedHashMap<Integer, Integer>();
	protected Map<Integer, L2Spawn> _executionerSpawns = new LinkedHashMap<Integer, L2Spawn>();
	protected Map<Integer, L2Spawn> _keyBoxSpawns = new LinkedHashMap<Integer, L2Spawn>();
	protected Map<Integer, L2Spawn> _mysteriousBoxSpawns = new LinkedHashMap<Integer, L2Spawn>();
	protected Map<Integer, L2Spawn> _shadowSpawns = new LinkedHashMap<Integer, L2Spawn>();
	protected Map<Integer, List<L2Spawn>> _dukeFinalMobs = new LinkedHashMap<Integer, List<L2Spawn>>();
	protected Map<Integer, List<L2SepulcherMonsterInstance>> _dukeMobs =
			new LinkedHashMap<Integer, List<L2SepulcherMonsterInstance>>();
	protected Map<Integer, List<L2Spawn>> _emperorsGraveNpcs = new LinkedHashMap<Integer, List<L2Spawn>>();
	protected Map<Integer, List<L2Spawn>> _magicalMonsters = new LinkedHashMap<Integer, List<L2Spawn>>();
	protected Map<Integer, List<L2Spawn>> _physicalMonsters = new LinkedHashMap<Integer, List<L2Spawn>>();
	protected Map<Integer, List<L2SepulcherMonsterInstance>> _viscountMobs =
			new LinkedHashMap<Integer, List<L2SepulcherMonsterInstance>>();
	
	protected List<L2Spawn> _physicalSpawns;
	protected List<L2Spawn> _magicalSpawns;
	protected List<L2Spawn> _managers;
	protected List<L2Spawn> _dukeFinalSpawns;
	protected List<L2Spawn> _emperorsGraveSpawns;
	protected List<L2Npc> _allMobs = new ArrayList<L2Npc>();
	
	protected long _coolDownTimeEnd = 0;
	protected long _entryTimeEnd = 0;
	protected long _warmUpTimeEnd = 0;
	protected long _attackTimeEnd = 0;
	
	protected byte _newCycleMin = 55;
	
	protected boolean _firstTimeRun;
	
	public static final FourSepulchersManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	public void setUnspawn()
	{
	}
	
	@Override
	public void init()
	{
		// The sepulchers are one part of the world. Without their manager the rest of the ids are absent too.
		if (NpcTable.getInstance().getTemplate(31921) == null)
		{
			_log.info("World has no sepulchers");
			return;
		}
		
		if (_changeCoolDownTimeTask != null)
			_changeCoolDownTimeTask.cancel(true);
		if (_changeEntryTimeTask != null)
			_changeEntryTimeTask.cancel(true);
		if (_changeWarmUpTimeTask != null)
			_changeWarmUpTimeTask.cancel(true);
		if (_changeAttackTimeTask != null)
			_changeAttackTimeTask.cancel(true);
		
		_changeCoolDownTimeTask = null;
		_changeEntryTimeTask = null;
		_changeWarmUpTimeTask = null;
		_changeAttackTimeTask = null;
		
		_inEntryTime = false;
		_inWarmUpTime = false;
		_inAttackTime = false;
		_inCoolDownTime = false;
		
		_firstTimeRun = true;
		initFixedInfo();
		loadMysteriousBox();
		initKeyBoxSpawns();
		loadPhysicalMonsters();
		loadMagicalMonsters();
		initLocationShadowSpawns();
		initExecutionerSpawns();
		loadDukeMonsters();
		loadEmperorsGraveMonsters();
		spawnManagers();
		timeSelector();
		
	}
	
	// Phase select on server launch
	protected void timeSelector()
	{
		timeCalculator();
		long currentTime = System.currentTimeMillis();
		// If current time >= time of entry beginning and if current time < time of entry beginning + time of entry end
		if (currentTime >= _coolDownTimeEnd && currentTime < _entryTimeEnd) // Entry time check
		{
			cleanUp();
			_changeEntryTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeEntryTime(), 0);
			_log.info("FourSepulchersManager: Beginning in Entry time");
		}
		else if (currentTime >= _entryTimeEnd && currentTime < _warmUpTimeEnd) // Warmup time check
		{
			cleanUp();
			_changeWarmUpTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeWarmUpTime(), 0);
			_log.info("FourSepulchersManager: Beginning in WarmUp time");
		}
		else if (currentTime >= _warmUpTimeEnd && currentTime < _attackTimeEnd) // Attack time check
		{
			cleanUp();
			_changeAttackTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeAttackTime(), 0);
			_log.info("FourSepulchersManager: Beginning in Attack time");
		}
		else
		// Else cooldown time and without cleanup because it's already implemented
		{
			_changeCoolDownTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeCoolDownTime(), 0);
			_log.info("FourSepulchersManager: Beginning in Cooldown time");
		}
	}
	
	// Phase end times calculator
	protected void timeCalculator()
	{
		Calendar tmp = Calendar.getInstance();
		if (tmp.get(Calendar.MINUTE) < _newCycleMin)
			tmp.set(Calendar.HOUR, Calendar.getInstance().get(Calendar.HOUR) - 1);
		tmp.set(Calendar.MINUTE, _newCycleMin);
		_coolDownTimeEnd = tmp.getTimeInMillis();
		_entryTimeEnd = _coolDownTimeEnd + Config.ALT_FS_TIME_ENTRY * 60000;
		_warmUpTimeEnd = _entryTimeEnd + Config.ALT_FS_TIME_WARMUP * 60000;
		_attackTimeEnd = _warmUpTimeEnd + Config.ALT_FS_TIME_ATTACK * 60000;
	}
	
	protected void cleanUp()
	{
		for (L2Player player : getPlayersInside())
		{
			player.teleToLocation(TeleportWhereType.Town);
		}
		
		deleteAllMobs();
		
		closeAllDoors();
		
		_hallInUse.clear();
		_hallInUse.put(31921, false);
		_hallInUse.put(31922, false);
		_hallInUse.put(31923, false);
		_hallInUse.put(31924, false);
		
		if (!_archonSpawned.isEmpty())
		{
			Set<Integer> npcIdSet = _archonSpawned.keySet();
			for (int npcId : npcIdSet)
			{
				_archonSpawned.put(npcId, false);
			}
		}
	}
	
	protected void spawnManagers()
	{
		_managers = new ArrayList<L2Spawn>();
		
		int i = 31921;
		for (L2Spawn spawnDat; i <= 31924; i++)
		{
			if (i < 31921 || i > 31924)
				continue;
			L2NpcTemplate template1 = NpcTable.getInstance().getTemplate(i);
			if (template1 == null)
				continue;
			spawnDat = new L2Spawn(template1);
			spawnDat.setAmount(1);
			spawnDat.setRespawnDelay(60);
			switch (i)
			{
				case 31921: // Conquerors
					spawnDat.setLocx(181061);
					spawnDat.setLocy(-85595);
					spawnDat.setLocz(-7200);
					spawnDat.setHeading(-32584);
					break;
				case 31922: // Emperors
					spawnDat.setLocx(179292);
					spawnDat.setLocy(-88981);
					spawnDat.setLocz(-7200);
					spawnDat.setHeading(-33272);
					break;
				case 31923: // Sages
					spawnDat.setLocx(173202);
					spawnDat.setLocy(-87004);
					spawnDat.setLocz(-7200);
					spawnDat.setHeading(-16248);
					break;
				case 31924: // Judges
					spawnDat.setLocx(175606);
					spawnDat.setLocy(-82853);
					spawnDat.setLocz(-7200);
					spawnDat.setHeading(-16248);
					break;
			}
			_managers.add(spawnDat);
			SpawnTable.getInstance().addNewSpawn(spawnDat, false);
			spawnDat.doSpawn();
			spawnDat.startRespawn();
			_log.info("FourSepulchersManager: Spawned " + spawnDat.getTemplate().getName());
		}
	}
	
	protected void initFixedInfo()
	{
		_startHallSpawns.put(31921, _startHallSpawn[0]);
		_startHallSpawns.put(31922, _startHallSpawn[1]);
		_startHallSpawns.put(31923, _startHallSpawn[2]);
		_startHallSpawns.put(31924, _startHallSpawn[3]);
		
		_hallInUse.put(31921, false);
		_hallInUse.put(31922, false);
		_hallInUse.put(31923, false);
		_hallInUse.put(31924, false);
		
		_hallGateKeepers.put(31925, 25150012);
		_hallGateKeepers.put(31926, 25150013);
		_hallGateKeepers.put(31927, 25150014);
		_hallGateKeepers.put(31928, 25150015);
		_hallGateKeepers.put(31929, 25150016);
		_hallGateKeepers.put(31930, 25150002);
		_hallGateKeepers.put(31931, 25150003);
		_hallGateKeepers.put(31932, 25150004);
		_hallGateKeepers.put(31933, 25150005);
		_hallGateKeepers.put(31934, 25150006);
		_hallGateKeepers.put(31935, 25150032);
		_hallGateKeepers.put(31936, 25150033);
		_hallGateKeepers.put(31937, 25150034);
		_hallGateKeepers.put(31938, 25150035);
		_hallGateKeepers.put(31939, 25150036);
		_hallGateKeepers.put(31940, 25150022);
		_hallGateKeepers.put(31941, 25150023);
		_hallGateKeepers.put(31942, 25150024);
		_hallGateKeepers.put(31943, 25150025);
		_hallGateKeepers.put(31944, 25150026);
		
		_keyBoxNpc.put(18120, 31455);
		_keyBoxNpc.put(18121, 31455);
		_keyBoxNpc.put(18122, 31455);
		_keyBoxNpc.put(18123, 31455);
		_keyBoxNpc.put(18124, 31456);
		_keyBoxNpc.put(18125, 31456);
		_keyBoxNpc.put(18126, 31456);
		_keyBoxNpc.put(18127, 31456);
		_keyBoxNpc.put(18128, 31457);
		_keyBoxNpc.put(18129, 31457);
		_keyBoxNpc.put(18130, 31457);
		_keyBoxNpc.put(18131, 31457);
		_keyBoxNpc.put(18149, 31458);
		_keyBoxNpc.put(18150, 31459);
		_keyBoxNpc.put(18151, 31459);
		_keyBoxNpc.put(18152, 31459);
		_keyBoxNpc.put(18153, 31459);
		_keyBoxNpc.put(18154, 31460);
		_keyBoxNpc.put(18155, 31460);
		_keyBoxNpc.put(18156, 31460);
		_keyBoxNpc.put(18157, 31460);
		_keyBoxNpc.put(18158, 31461);
		_keyBoxNpc.put(18159, 31461);
		_keyBoxNpc.put(18160, 31461);
		_keyBoxNpc.put(18161, 31461);
		_keyBoxNpc.put(18162, 31462);
		_keyBoxNpc.put(18163, 31462);
		_keyBoxNpc.put(18164, 31462);
		_keyBoxNpc.put(18165, 31462);
		_keyBoxNpc.put(18183, 31463);
		_keyBoxNpc.put(18184, 31464);
		_keyBoxNpc.put(18212, 31465);
		_keyBoxNpc.put(18213, 31465);
		_keyBoxNpc.put(18214, 31465);
		_keyBoxNpc.put(18215, 31465);
		_keyBoxNpc.put(18216, 31466);
		_keyBoxNpc.put(18217, 31466);
		_keyBoxNpc.put(18218, 31466);
		_keyBoxNpc.put(18219, 31466);
		
		_victim.put(18150, 18158);
		_victim.put(18151, 18159);
		_victim.put(18152, 18160);
		_victim.put(18153, 18161);
		_victim.put(18154, 18162);
		_victim.put(18155, 18163);
		_victim.put(18156, 18164);
		_victim.put(18157, 18165);
	}
	
	private void loadMysteriousBox()
	{
		Connection con = null;
		
		_mysteriousBoxSpawns.clear();
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s, key_npc_template_id FROM four_sepulchers_spawn WHERE spawn_type = 'MYSTERIOUS_BOX' ORDER BY id");
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
					int keyNpcId = rset.getInt("key_npc_template_id");
					_mysteriousBoxSpawns.put(keyNpcId, spawnDat);
				}
				else
				{
					_log.warn("FourSepulchersManager.LoadMysteriousBox: Data missing in NPC table for ID: "
							+ rset.getInt("npc_template_id") + ".");
				}
			}
			
			rset.close();
			statement.close();
			_log.info("FourSepulchersManager: Loaded " + _mysteriousBoxSpawns.size() + " Mysterious-Box spawns.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.error("FourSepulchersManager.LoadMysteriousBox: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void initKeyBoxSpawns()
	{
		L2Spawn spawnDat;
		L2NpcTemplate template;
		
		for (int keyNpcId : _keyBoxNpc.keySet())
		{
			try
			{
				template = NpcTable.getInstance().getTemplate(_keyBoxNpc.get(keyNpcId));
				if (template != null)
				{
					spawnDat = new L2Spawn(template);
					spawnDat.setAmount(1);
					spawnDat.setLocx(0);
					spawnDat.setLocy(0);
					spawnDat.setLocz(0);
					spawnDat.setHeading(0);
					spawnDat.setRespawnDelay(3600);
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_keyBoxSpawns.put(keyNpcId, spawnDat);
				}
				else
				{
					_log.warn("FourSepulchersManager.InitKeyBoxSpawns: Data missing in NPC table for ID: "
							+ _keyBoxNpc.get(keyNpcId) + ".");
				}
			}
			catch (Exception e)
			{
				_log.error("FourSepulchersManager.InitKeyBoxSpawns: Spawn could not be initialized: ", e);
			}
		}
	}
	
	private void loadPhysicalMonsters()
	{
		_physicalMonsters.clear();
		
		int loaded = 0;
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement1 =
					con.prepareStatement("SELECT DISTINCT key_npc_template_id FROM four_sepulchers_spawn WHERE spawn_type = 'PHYSICAL_MONSTER' ORDER BY key_npc_template_id");
			ResultSet rset1 = statement1.executeQuery();
			while (rset1.next())
			{
				int keyNpcId = rset1.getInt("key_npc_template_id");
				
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s, key_npc_template_id FROM four_sepulchers_spawn WHERE key_npc_template_id = ? AND spawn_type = 'PHYSICAL_MONSTER' ORDER BY id");
				statement2.setInt(1, keyNpcId);
				ResultSet rset2 = statement2.executeQuery();
				
				L2Spawn spawnDat;
				L2NpcTemplate template1;
				
				_physicalSpawns = new ArrayList<L2Spawn>();
				
				while (rset2.next())
				{
					template1 = NpcTable.getInstance().getTemplate(rset2.getInt("npc_template_id"));
					if (template1 != null)
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setAmount(rset2.getInt("npc_count"));
						spawnDat.setLocx(rset2.getInt("x"));
						spawnDat.setLocy(rset2.getInt("y"));
						spawnDat.setLocz(rset2.getInt("z"));
						spawnDat.setHeading(rset2.getInt("heading"));
						spawnDat.setRespawnDelay(rset2.getInt("respawn_delay_s"));
						SpawnTable.getInstance().addNewSpawn(spawnDat, false);
						_physicalSpawns.add(spawnDat);
						loaded++;
					}
					else
					{
						_log.warn("FourSepulchersManager.LoadPhysicalMonsters: Data missing in NPC table for ID: "
								+ rset2.getInt("npc_template_id") + ".");
					}
				}
				
				rset2.close();
				statement2.close();
				_physicalMonsters.put(keyNpcId, _physicalSpawns);
			}
			
			rset1.close();
			statement1.close();
			_log.info("FourSepulchersManager: Loaded " + loaded + " Physical type monsters spawns.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.error("FourSepulchersManager.LoadPhysicalMonsters: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void loadMagicalMonsters()
	{
		_magicalMonsters.clear();
		
		int loaded = 0;
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement1 =
					con.prepareStatement("SELECT DISTINCT key_npc_template_id FROM four_sepulchers_spawn WHERE spawn_type = 'MAGICAL_MONSTER' ORDER BY key_npc_template_id");
			ResultSet rset1 = statement1.executeQuery();
			while (rset1.next())
			{
				int keyNpcId = rset1.getInt("key_npc_template_id");
				
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s, key_npc_template_id FROM four_sepulchers_spawn WHERE key_npc_template_id = ? AND spawn_type = 'MAGICAL_MONSTER' ORDER BY id");
				statement2.setInt(1, keyNpcId);
				ResultSet rset2 = statement2.executeQuery();
				
				L2Spawn spawnDat;
				L2NpcTemplate template1;
				
				_magicalSpawns = new ArrayList<L2Spawn>();
				
				while (rset2.next())
				{
					template1 = NpcTable.getInstance().getTemplate(rset2.getInt("npc_template_id"));
					if (template1 != null)
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setAmount(rset2.getInt("npc_count"));
						spawnDat.setLocx(rset2.getInt("x"));
						spawnDat.setLocy(rset2.getInt("y"));
						spawnDat.setLocz(rset2.getInt("z"));
						spawnDat.setHeading(rset2.getInt("heading"));
						spawnDat.setRespawnDelay(rset2.getInt("respawn_delay_s"));
						SpawnTable.getInstance().addNewSpawn(spawnDat, false);
						_magicalSpawns.add(spawnDat);
						loaded++;
					}
					else
					{
						_log.warn("FourSepulchersManager.LoadMagicalMonsters: Data missing in NPC table for ID: "
								+ rset2.getInt("npc_template_id") + ".");
					}
				}
				
				rset2.close();
				statement2.close();
				_magicalMonsters.put(keyNpcId, _magicalSpawns);
			}
			
			rset1.close();
			statement1.close();
			_log.info("FourSepulchersManager: Loaded " + loaded + " Magical type monsters spawns.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.error("FourSepulchersManager.LoadMagicalMonsters: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void loadDukeMonsters()
	{
		_dukeFinalMobs.clear();
		_archonSpawned.clear();
		
		int loaded = 0;
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement1 =
					con.prepareStatement("SELECT DISTINCT key_npc_template_id FROM four_sepulchers_spawn WHERE spawn_type = 'DUKE_FINAL_MONSTER' ORDER BY key_npc_template_id");
			ResultSet rset1 = statement1.executeQuery();
			while (rset1.next())
			{
				int keyNpcId = rset1.getInt("key_npc_template_id");
				
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s, key_npc_template_id FROM four_sepulchers_spawn WHERE key_npc_template_id = ? AND spawn_type = 'DUKE_FINAL_MONSTER' ORDER BY id");
				statement2.setInt(1, keyNpcId);
				ResultSet rset2 = statement2.executeQuery();
				
				L2Spawn spawnDat;
				L2NpcTemplate template1;
				
				_dukeFinalSpawns = new ArrayList<L2Spawn>();
				
				while (rset2.next())
				{
					template1 = NpcTable.getInstance().getTemplate(rset2.getInt("npc_template_id"));
					if (template1 != null)
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setAmount(rset2.getInt("npc_count"));
						spawnDat.setLocx(rset2.getInt("x"));
						spawnDat.setLocy(rset2.getInt("y"));
						spawnDat.setLocz(rset2.getInt("z"));
						spawnDat.setHeading(rset2.getInt("heading"));
						spawnDat.setRespawnDelay(rset2.getInt("respawn_delay_s"));
						SpawnTable.getInstance().addNewSpawn(spawnDat, false);
						_dukeFinalSpawns.add(spawnDat);
						loaded++;
					}
					else
					{
						_log.warn("FourSepulchersManager.LoadDukeMonsters: Data missing in NPC table for ID: "
								+ rset2.getInt("npc_template_id") + ".");
					}
				}
				
				rset2.close();
				statement2.close();
				_dukeFinalMobs.put(keyNpcId, _dukeFinalSpawns);
				_archonSpawned.put(keyNpcId, false);
			}
			
			rset1.close();
			statement1.close();
			_log.info("FourSepulchersManager: loaded " + loaded + " Church of duke monsters spawns.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.error("FourSepulchersManager.LoadDukeMonsters: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void loadEmperorsGraveMonsters()
	{
		_emperorsGraveNpcs.clear();
		
		int loaded = 0;
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement1 =
					con.prepareStatement("SELECT DISTINCT key_npc_template_id FROM four_sepulchers_spawn WHERE spawn_type = 'EMPEROR_GRAVE_MONSTER' ORDER BY key_npc_template_id");
			ResultSet rset1 = statement1.executeQuery();
			while (rset1.next())
			{
				int keyNpcId = rset1.getInt("key_npc_template_id");
				
				PreparedStatement statement2 =
						con.prepareStatement("SELECT npc_count, npc_template_id, x, y, z, heading, respawn_delay_s, key_npc_template_id FROM four_sepulchers_spawn WHERE key_npc_template_id = ? AND spawn_type = 'EMPEROR_GRAVE_MONSTER' ORDER BY id");
				statement2.setInt(1, keyNpcId);
				ResultSet rset2 = statement2.executeQuery();
				
				L2Spawn spawnDat;
				L2NpcTemplate template1;
				
				_emperorsGraveSpawns = new ArrayList<L2Spawn>();
				
				while (rset2.next())
				{
					template1 = NpcTable.getInstance().getTemplate(rset2.getInt("npc_template_id"));
					if (template1 != null)
					{
						spawnDat = new L2Spawn(template1);
						spawnDat.setAmount(rset2.getInt("npc_count"));
						spawnDat.setLocx(rset2.getInt("x"));
						spawnDat.setLocy(rset2.getInt("y"));
						spawnDat.setLocz(rset2.getInt("z"));
						spawnDat.setHeading(rset2.getInt("heading"));
						spawnDat.setRespawnDelay(rset2.getInt("respawn_delay_s"));
						SpawnTable.getInstance().addNewSpawn(spawnDat, false);
						_emperorsGraveSpawns.add(spawnDat);
						loaded++;
					}
					else
					{
						_log.warn("FourSepulchersManager.LoadEmperorsGraveMonsters: Data missing in NPC table for ID: "
								+ rset2.getInt("npc_template_id") + ".");
					}
				}
				
				rset2.close();
				statement2.close();
				_emperorsGraveNpcs.put(keyNpcId, _emperorsGraveSpawns);
			}
			
			rset1.close();
			statement1.close();
			_log.info("FourSepulchersManager: loaded " + loaded + " Emperor's grave NPC spawns.");
		}
		catch (Exception e)
		{
			// Problem with initializing spawn, go to next one
			_log.error("FourSepulchersManager.LoadEmperorsGraveMonsters: Spawn could not be initialized: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void initLocationShadowSpawns()
	{
		int locNo = Rnd.get(4);
		final int[] gateKeeper = { 31929, 31934, 31939, 31944 };
		
		L2Spawn spawnDat;
		L2NpcTemplate template;
		
		_shadowSpawns.clear();
		
		for (int i = 0; i <= 3; i++)
		{
			template = NpcTable.getInstance().getTemplate(_shadowSpawnLoc[locNo][i][0]);
			if (template != null)
			{
				try
				{
					spawnDat = new L2Spawn(template);
					spawnDat.setAmount(1);
					spawnDat.setLocx(_shadowSpawnLoc[locNo][i][1]);
					spawnDat.setLocy(_shadowSpawnLoc[locNo][i][2]);
					spawnDat.setLocz(_shadowSpawnLoc[locNo][i][3]);
					spawnDat.setHeading(_shadowSpawnLoc[locNo][i][4]);
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					int keyNpcId = gateKeeper[i];
					_shadowSpawns.put(keyNpcId, spawnDat);
				}
				catch (Exception e)
				{
					_log.error("Error on InitLocationShadowSpawns", e);
				}
			}
			else
			{
				_log.error("FourSepulchersManager.InitLocationShadowSpawns: Data missing in NPC table for ID: "
						+ _shadowSpawnLoc[locNo][i][0] + ".");
			}
		}
	}
	
	protected void initExecutionerSpawns()
	{
		L2Spawn spawnDat;
		L2NpcTemplate template;
		
		for (int keyNpcId : _victim.keySet())
		{
			try
			{
				template = NpcTable.getInstance().getTemplate(_victim.get(keyNpcId));
				if (template != null)
				{
					spawnDat = new L2Spawn(template);
					spawnDat.setAmount(1);
					spawnDat.setLocx(0);
					spawnDat.setLocy(0);
					spawnDat.setLocz(0);
					spawnDat.setHeading(0);
					spawnDat.setRespawnDelay(3600);
					SpawnTable.getInstance().addNewSpawn(spawnDat, false);
					_executionerSpawns.put(keyNpcId, spawnDat);
				}
				else
				{
					_log.warn("FourSepulchersManager.InitExecutionerSpawns: Data missing in NPC table for ID: "
							+ _victim.get(keyNpcId) + ".");
				}
			}
			catch (Exception e)
			{
				_log.error("FourSepulchersManager.InitExecutionerSpawns: Spawn could not be initialized: ", e);
			}
		}
	}
	
	public boolean isEntryTime()
	{
		return _inEntryTime;
	}
	
	public boolean isAttackTime()
	{
		return _inAttackTime;
	}
	
	public synchronized void tryEntry(L2Npc npc, L2Player player)
	{
		int npcId = npc.getNpcId();
		switch (npcId)
		{
		// ID ok
			case 31921:
			case 31922:
			case 31923:
			case 31924:
				break;
			// ID not ok
			default:
				if (!player.isGM())
				{
					_log.warn("Player " + player.getName() + "(" + player.getObjectId()
							+ ") tried to cheat in four sepulchers.");
					Util.handleIllegalPlayerAction(player, "Warning!! Character " + player.getName()
							+ " tried to enter four sepulchers with invalid npc id.", Config.DEFAULT_PUNISH);
				}
				return;
		}
		
		if (_hallInUse.get(npcId))
		{
			showHtmlFile(player, npcId + "-FULL.htm", npc, null);
			return;
		}
		
		if (Config.ALT_FS_PARTY_MEMBER_COUNT > 1)
		{
			if (!player.isInParty() || player.getParty().getMemberCount() < Config.ALT_FS_PARTY_MEMBER_COUNT)
			{
				showHtmlFile(player, npcId + "-SP.htm", npc, null);
				return;
			}
			
			if (!player.getParty().isLeader(player))
			{
				showHtmlFile(player, npcId + "-NL.htm", npc, null);
				return;
			}
			
			for (L2Player mem : player.getParty().getPartyMembers())
			{
				QuestState qs = mem.getQuestState(QUEST_ID);
				if (qs == null || (!qs.isStarted() && !qs.isCompleted()))
				{
					showHtmlFile(player, npcId + "-NS.htm", npc, mem);
					return;
				}
				if (mem.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
				{
					showHtmlFile(player, npcId + "-SE.htm", npc, mem);
					return;
				}
				
				if (player.getWeightPenalty() >= 3)
				{
					mem.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return;
				}
			}
		}
		else if (player.isInParty())
		{
			if (!player.getParty().isLeader(player))
			{
				showHtmlFile(player, npcId + "-NL.htm", npc, null);
				return;
			}
			for (L2Player mem : player.getParty().getPartyMembers())
			{
				QuestState qs = mem.getQuestState(QUEST_ID);
				if (qs == null || (!qs.isStarted() && !qs.isCompleted()))
				{
					showHtmlFile(player, npcId + "-NS.htm", npc, mem);
					return;
				}
				if (mem.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
				{
					showHtmlFile(player, npcId + "-SE.htm", npc, mem);
					return;
				}
				if (player.getWeightPenalty() >= 3)
				{
					mem.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return;
				}
			}
		}
		else
		{
			QuestState qs = player.getQuestState(QUEST_ID);
			if (qs == null || (!qs.isStarted() && !qs.isCompleted()))
			{
				showHtmlFile(player, npcId + "-NS.htm", npc, player);
				return;
			}
			if (player.getInventory().getItemByItemId(ENTRANCE_PASS) == null)
			{
				showHtmlFile(player, npcId + "-SE.htm", npc, player);
				return;
			}
			
			if (player.getWeightPenalty() >= 3)
			{
				player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
				return;
			}
		}
		
		if (!isEntryTime())
		{
			showHtmlFile(player, npcId + "-NE.htm", npc, null);
			return;
		}
		
		showHtmlFile(player, npcId + "-OK.htm", npc, null);
		
		entry(npcId, player);
	}
	
	private void entry(int npcId, L2Player player)
	{
		int[] Location = _startHallSpawns.get(npcId);
		int driftx;
		int drifty;
		
		if (Config.ALT_FS_PARTY_MEMBER_COUNT > 1)
		{
			List<L2Player> members = new ArrayList<L2Player>();
			for (L2Player mem : player.getParty().getPartyMembers())
			{
				if (!mem.isDead() && Util.checkIfInRange(700, player, mem, true))
				{
					members.add(mem);
				}
			}
			
			for (L2Player mem : members)
			{
				driftx = Rnd.get(-80, 80);
				drifty = Rnd.get(-80, 80);
				mem.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
				mem.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, mem, true);
				if (mem.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
				{
					mem.addItem("Quest", USED_PASS, 1, mem, true);
				}
				
				L2ItemInstance hallsKey = mem.getInventory().getItemByItemId(CHAPEL_KEY);
				if (hallsKey != null)
				{
					mem.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), mem, true);
				}
			}
			
			_hallInUse.put(npcId, true);
		}
		else if (player.isInParty())
		{
			List<L2Player> members = new ArrayList<L2Player>();
			for (L2Player mem : player.getParty().getPartyMembers())
			{
				if (!mem.isDead() && Util.checkIfInRange(700, player, mem, true))
				{
					members.add(mem);
				}
			}
			
			for (L2Player mem : members)
			{
				driftx = Rnd.get(-80, 80);
				drifty = Rnd.get(-80, 80);
				mem.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
				mem.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, mem, true);
				if (mem.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
				{
					mem.addItem("Quest", USED_PASS, 1, mem, true);
				}
				
				L2ItemInstance hallsKey = mem.getInventory().getItemByItemId(CHAPEL_KEY);
				if (hallsKey != null)
				{
					mem.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), mem, true);
				}
			}
			
			_hallInUse.put(npcId, true);
		}
		else
		{
			driftx = Rnd.get(-80, 80);
			drifty = Rnd.get(-80, 80);
			player.teleToLocation(Location[0] + driftx, Location[1] + drifty, Location[2]);
			player.destroyItemByItemId("Quest", ENTRANCE_PASS, 1, player, true);
			if (player.getInventory().getItemByItemId(ANTIQUE_BROOCH) == null)
			{
				player.addItem("Quest", USED_PASS, 1, player, true);
			}
			
			L2ItemInstance hallsKey = player.getInventory().getItemByItemId(CHAPEL_KEY);
			if (hallsKey != null)
			{
				player.destroyItemByItemId("Quest", CHAPEL_KEY, hallsKey.getCount(), player, true);
			}
			
			_hallInUse.put(npcId, true);
		}
	}
	
	public void spawnMysteriousBox(int npcId)
	{
		if (!isAttackTime())
			return;
		
		L2Spawn spawnDat = _mysteriousBoxSpawns.get(npcId);
		if (spawnDat != null)
		{
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
		}
	}
	
	public void spawnMonster(int npcId)
	{
		if (!isAttackTime())
			return;
		
		List<L2Spawn> monsterList;
		List<L2SepulcherMonsterInstance> mobs = new ArrayList<L2SepulcherMonsterInstance>();
		L2Spawn keyBoxMobSpawn;
		
		if (Rnd.get(2) == 0)
		{
			monsterList = (List<L2Spawn>)_physicalMonsters.get(npcId);
		}
		else
		{
			monsterList = (List<L2Spawn>)_magicalMonsters.get(npcId);
		}
		
		if (monsterList != null)
		{
			boolean spawnKeyBoxMob = false;
			boolean spawnedKeyBoxMob = false;
			
			for (L2Spawn spawnDat : monsterList)
			{
				if (spawnedKeyBoxMob)
				{
					spawnKeyBoxMob = false;
				}
				else
				{
					switch (npcId)
					{
						case 31469:
						case 31474:
						case 31479:
						case 31484:
							if (Rnd.get(48) == 0)
							{
								spawnKeyBoxMob = true;
								//_log.info("FourSepulchersManager.SpawnMonster: Set to spawn Church of Viscount Key Mob.");
							}
							break;
					}
				}
				
				L2SepulcherMonsterInstance mob = null;
				
				if (spawnKeyBoxMob)
				{
					try
					{
						L2NpcTemplate template = NpcTable.getInstance().getTemplate(18149);
						if (template != null)
						{
							keyBoxMobSpawn = new L2Spawn(template);
							keyBoxMobSpawn.setAmount(1);
							keyBoxMobSpawn.setLocx(spawnDat.getLocx());
							keyBoxMobSpawn.setLocy(spawnDat.getLocy());
							keyBoxMobSpawn.setLocz(spawnDat.getLocz());
							keyBoxMobSpawn.setHeading(spawnDat.getHeading());
							keyBoxMobSpawn.setRespawnDelay(3600);
							SpawnTable.getInstance().addNewSpawn(keyBoxMobSpawn, false);
							mob = (L2SepulcherMonsterInstance)keyBoxMobSpawn.doSpawn();
							keyBoxMobSpawn.stopRespawn();
						}
						else
						{
							_log.warn("FourSepulchersManager.SpawnMonster: Data missing in NPC table for ID: 18149");
						}
					}
					catch (Exception e)
					{
						_log.error("FourSepulchersManager.SpawnMonster: Spawn could not be initialized: ", e);
					}
					
					spawnedKeyBoxMob = true;
				}
				else
				{
					mob = (L2SepulcherMonsterInstance)spawnDat.doSpawn();
					spawnDat.stopRespawn();
				}
				
				if (mob != null)
				{
					mob.mysteriousBoxId = npcId;
					switch (npcId)
					{
						case 31469:
						case 31474:
						case 31479:
						case 31484:
						case 31472:
						case 31477:
						case 31482:
						case 31487:
							mobs.add(mob);
							break;
					}
					_allMobs.add(mob);
				}
			}
			
			switch (npcId)
			{
				case 31469:
				case 31474:
				case 31479:
				case 31484:
					_viscountMobs.put(npcId, mobs);
					break;
				
				case 31472:
				case 31477:
				case 31482:
				case 31487:
					_dukeMobs.put(npcId, mobs);
					break;
			}
		}
	}
	
	public synchronized boolean isViscountMobsAnnihilated(int npcId)
	{
		List<L2SepulcherMonsterInstance> mobs = (List<L2SepulcherMonsterInstance>)_viscountMobs.get(npcId);
		
		if (mobs == null)
			return true;
		
		for (L2SepulcherMonsterInstance mob : mobs)
		{
			if (!mob.isDead())
				return false;
		}
		
		return true;
	}
	
	public synchronized boolean isDukeMobsAnnihilated(int npcId)
	{
		List<L2SepulcherMonsterInstance> mobs = (List<L2SepulcherMonsterInstance>)_dukeMobs.get(npcId);
		
		if (mobs == null)
			return true;
		
		for (L2SepulcherMonsterInstance mob : mobs)
		{
			if (!mob.isDead())
				return false;
		}
		
		return true;
	}
	
	public void spawnKeyBox(L2Npc activeChar)
	{
		if (!isAttackTime())
			return;
		
		L2Spawn spawnDat = _keyBoxSpawns.get(activeChar.getNpcId());
		
		if (spawnDat != null)
		{
			spawnDat.setAmount(1);
			spawnDat.setLocx(activeChar.getX());
			spawnDat.setLocy(activeChar.getY());
			spawnDat.setLocz(activeChar.getZ());
			spawnDat.setHeading(activeChar.getHeading());
			spawnDat.setRespawnDelay(3600);
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
			
		}
	}
	
	public void spawnExecutionerOfHalisha(L2Npc activeChar)
	{
		if (!isAttackTime())
			return;
		
		L2Spawn spawnDat = _executionerSpawns.get(activeChar.getNpcId());
		
		if (spawnDat != null)
		{
			spawnDat.setAmount(1);
			spawnDat.setLocx(activeChar.getX());
			spawnDat.setLocy(activeChar.getY());
			spawnDat.setLocz(activeChar.getZ());
			spawnDat.setHeading(activeChar.getHeading());
			spawnDat.setRespawnDelay(3600);
			_allMobs.add(spawnDat.doSpawn());
			spawnDat.stopRespawn();
		}
	}
	
	public void spawnArchonOfHalisha(int npcId)
	{
		if (!isAttackTime())
			return;
		
		Boolean status = _archonSpawned.get(npcId);
		if (status != null && status)
			return;
		
		List<L2Spawn> monsterList = (List<L2Spawn>)_dukeFinalMobs.get(npcId);
		
		if (monsterList != null)
		{
			for (L2Spawn spawnDat : monsterList)
			{
				L2SepulcherMonsterInstance mob = (L2SepulcherMonsterInstance)spawnDat.doSpawn();
				spawnDat.stopRespawn();
				
				if (mob != null)
				{
					mob.mysteriousBoxId = npcId;
					_allMobs.add(mob);
				}
			}
			_archonSpawned.put(npcId, true);
		}
	}
	
	public void spawnEmperorsGraveNpc(int npcId)
	{
		if (!isAttackTime())
			return;
		
		List<L2Spawn> monsterList = (List<L2Spawn>)_emperorsGraveNpcs.get(npcId);
		
		if (monsterList != null)
		{
			for (L2Spawn spawnDat : monsterList)
			{
				_allMobs.add(spawnDat.doSpawn());
				spawnDat.stopRespawn();
			}
		}
	}
	
	protected void locationShadowSpawns()
	{
		int locNo = Rnd.get(4);
		final int[] gateKeeper = { 31929, 31934, 31939, 31944 };
		
		L2Spawn spawnDat;
		
		for (int i = 0; i <= 3; i++)
		{
			int keyNpcId = gateKeeper[i];
			spawnDat = _shadowSpawns.get(keyNpcId);
			spawnDat.setLocx(_shadowSpawnLoc[locNo][i][1]);
			spawnDat.setLocy(_shadowSpawnLoc[locNo][i][2]);
			spawnDat.setLocz(_shadowSpawnLoc[locNo][i][3]);
			spawnDat.setHeading(_shadowSpawnLoc[locNo][i][4]);
			_shadowSpawns.put(keyNpcId, spawnDat);
		}
	}
	
	public void spawnShadow(int npcId)
	{
		if (!isAttackTime())
			return;
		
		L2Spawn spawnDat = _shadowSpawns.get(npcId);
		if (spawnDat != null)
		{
			L2SepulcherMonsterInstance mob = (L2SepulcherMonsterInstance)spawnDat.doSpawn();
			spawnDat.stopRespawn();
			
			if (mob != null)
			{
				mob.mysteriousBoxId = npcId;
				_allMobs.add(mob);
			}
		}
	}
	
	public void checkAnnihilated(final L2Player player)
	{
		if (isPlayersAnnihilated())
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					onPartyAnnihilated(player);
				}
			}, 5000);
		}
	}
	
	public void onPartyAnnihilated(L2Player player)
	{
		if (player.getParty() != null)
		{
			for (L2Player mem : player.getParty().getPartyMembers())
			{
				if (!mem.isDead())
					break;
				int driftX = Rnd.get(-80, 80);
				int driftY = Rnd.get(-80, 80);
				mem.teleToLocation(169589 + driftX, -90493 + driftY, -2914);
			}
		}
		else
		{
			int driftX = Rnd.get(-80, 80);
			int driftY = Rnd.get(-80, 80);
			player.teleToLocation(169589 + driftX, -90493 + driftY, -2914);
		}
	}
	
	public void deleteAllMobs()
	{
		int delCnt = 0;
		for (L2Npc mob : _allMobs)
		{
			try
			{
				mob.getSpawn().stopRespawn();
				mob.deleteMe();
				delCnt++;
			}
			catch (Exception e)
			{
				_log.error("FourSepulchersManager: Failed deleting mob.", e);
			}
		}
		_allMobs.clear();
	}
	
	protected void closeAllDoors()
	{
		for (int doorId : _hallGateKeepers.values())
		{
			try
			{
				L2DoorInstance door = DoorTable.getInstance().getDoor(doorId);
				if (door != null)
				{
					door.closeMe();
				}
				else
				{
					_log.warn("FourSepulchersManager: Attempted to close undefined door. doorId: " + doorId);
				}
			}
			catch (Exception e)
			{
				_log.error("FourSepulchersManager: Failed closing door", e);
			}
		}
	}
	
	protected byte minuteSelect(byte min)
	{
		switch (min % 5)
		{
			case 0:
				return min;
			case 1:
				return (byte)(min - 1);
			case 2:
				return (byte)(min - 2);
			case 3:
				return (byte)(min + 2);
			default:
				return (byte)(min + 1);
		}
	}
	
	public void managerSay(byte min)
	{
		// For attack phase, sending message every 5 minutes
		if (_inAttackTime)
		{
			// Do not shout when < 5 minutes
			if (min < 5)
				return;
			
			min = minuteSelect(min);
			
			String msg = min + " minute(s) have passed."; // Now this is a proper message ^^
			
			if (min == 90)
				msg = "Game over. The teleport will appear momentarily";
			
			for (L2Spawn temp : _managers)
			{
				if (temp == null)
				{
					continue;
				}
				if (!(temp.getLastSpawn() instanceof L2SepulcherNpcInstance))
				{
					_log.warn("FourSepulchersManager: managerSay(): manager is not Sepulcher instance");
					continue;
				}
				// Hall not used right now, so its manager will not tell you anything :)
				// If you don't need this - delete next two lines.
				if (!_hallInUse.get(temp.getNpcId()))
					continue;
				
				((L2SepulcherNpcInstance)temp.getLastSpawn()).sayInShout(msg);
			}
		}
		else if (_inEntryTime)
		{
			String msg1 = "You may now enter the Sepulcher";
			String msg2 =
					"If you place your hand on the stone statue in front of each sepulcher,"
							+ " you will be able to enter";
			
			for (L2Spawn temp : _managers)
			{
				if (temp == null)
				{
					_log.warn("FourSepulchersManager: Something goes wrong in managerSay()...");
					continue;
				}
				if (!(temp.getLastSpawn() instanceof L2SepulcherNpcInstance))
				{
					_log.warn("FourSepulchersManager: Something goes wrong in managerSay()...");
					continue;
				}
				((L2SepulcherNpcInstance)temp.getLastSpawn()).sayInShout(msg1);
				((L2SepulcherNpcInstance)temp.getLastSpawn()).sayInShout(msg2);
			}
		}
	}
	
	protected class ManagerSay implements Runnable
	{
		@Override
		public void run()
		{
			if (_inAttackTime)
			{
				Calendar tmp = Calendar.getInstance();
				tmp.setTimeInMillis(System.currentTimeMillis() - _warmUpTimeEnd);
				if (tmp.get(Calendar.MINUTE) + 5 < Config.ALT_FS_TIME_ATTACK)
				{
					managerSay((byte)tmp.get(Calendar.MINUTE)); // Byte because minute cannot be more than 59
					ThreadPoolManager.getInstance().scheduleGeneral(new ManagerSay(), 5 * 60000);
				}
				// Attack time ending chat
				else if (tmp.get(Calendar.MINUTE) + 5 >= Config.ALT_FS_TIME_ATTACK)
				{
					managerSay((byte)90); // Sending a unique Id :D
				}
			}
			else if (_inEntryTime)
				managerSay((byte)0);
		}
	}
	
	protected class ChangeEntryTime implements Runnable
	{
		@Override
		public void run()
		{
			//_log.info("FourSepulchersManager:In Entry Time");
			_inEntryTime = true;
			_inWarmUpTime = false;
			_inAttackTime = false;
			_inCoolDownTime = false;
			
			long interval = 0;
			// If this is first launch - search time when entry time will be ended:
			// Counting difference between time when entry time ends and current time
			// and then launching change time task
			if (_firstTimeRun)
				interval = _entryTimeEnd - System.currentTimeMillis();
			else
				interval = Config.ALT_FS_TIME_ENTRY * 60000; // else use stupid method
			// Launching saying process...
			ThreadPoolManager.getInstance().executeTask(new ManagerSay());
			_changeWarmUpTimeTask = ThreadPoolManager.getInstance().scheduleEffect(new ChangeWarmUpTime(), interval);
			if (_changeEntryTimeTask != null)
			{
				_changeEntryTimeTask.cancel(true);
				_changeEntryTimeTask = null;
			}
		}
	}
	
	protected class ChangeWarmUpTime implements Runnable
	{
		@Override
		public void run()
		{
			//_log.info("FourSepulchersManager:In Warm-Up Time");
			_inEntryTime = true;
			_inWarmUpTime = false;
			_inAttackTime = false;
			_inCoolDownTime = false;
			
			long interval = 0;
			// Searching time when warmup time will be ended:
			// Counting difference between time when warmup time ends and current time
			// and then launching change time task
			if (_firstTimeRun)
				interval = _warmUpTimeEnd - System.currentTimeMillis();
			else
				interval = Config.ALT_FS_TIME_WARMUP * 60000;
			_changeAttackTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeAttackTime(), interval);
			
			if (_changeWarmUpTimeTask != null)
			{
				_changeWarmUpTimeTask.cancel(true);
				_changeWarmUpTimeTask = null;
			}
		}
	}
	
	protected class ChangeAttackTime implements Runnable
	{
		@Override
		public void run()
		{
			//_log.info("FourSepulchersManager:In Attack Time");
			_inEntryTime = false;
			_inWarmUpTime = false;
			_inAttackTime = true;
			_inCoolDownTime = false;
			
			locationShadowSpawns();
			
			spawnMysteriousBox(31921);
			spawnMysteriousBox(31922);
			spawnMysteriousBox(31923);
			spawnMysteriousBox(31924);
			
			if (!_firstTimeRun)
				_warmUpTimeEnd = System.currentTimeMillis();
			
			long interval = 0;
			// Say task
			if (_firstTimeRun)
			{
				for (double min = Calendar.getInstance().get(Calendar.MINUTE); min < _newCycleMin; min++)
				{
					// Looking for next shout time....
					if (min % 5 == 0)//check if min can be divided by 5
					{
						_log.info(Calendar.getInstance().getTime() + " Atk announce scheduled to " + min
								+ " minute of this hour.");
						Calendar inter = Calendar.getInstance();
						inter.set(Calendar.MINUTE, (int)min);
						ThreadPoolManager.getInstance().scheduleGeneral(new ManagerSay(),
								inter.getTimeInMillis() - System.currentTimeMillis());
						break;
					}
				}
			}
			else
				ThreadPoolManager.getInstance().scheduleGeneral(new ManagerSay(), 5 * 60400);
			// Searching time when attack time will be ended:
			// Counting difference between time when attack time ends and current time
			// and then launching change time task
			if (_firstTimeRun)
				interval = _attackTimeEnd - System.currentTimeMillis();
			else
				interval = Config.ALT_FS_TIME_ATTACK * 60000;
			_changeCoolDownTimeTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new ChangeCoolDownTime(), interval);
			
			if (_changeAttackTimeTask != null)
			{
				_changeAttackTimeTask.cancel(true);
				_changeAttackTimeTask = null;
			}
		}
	}
	
	protected class ChangeCoolDownTime implements Runnable
	{
		@Override
		public void run()
		{
			//_log.info("FourSepulchersManager:In Cool-Down Time");
			_inEntryTime = false;
			_inWarmUpTime = false;
			_inAttackTime = false;
			_inCoolDownTime = true;
			
			cleanUp();
			
			Calendar time = Calendar.getInstance();
			// One hour = 55th min to 55 min of next hour, so we check for this, also check for first launch
			if (Calendar.getInstance().get(Calendar.MINUTE) > _newCycleMin && !_firstTimeRun)
				time.set(Calendar.HOUR, Calendar.getInstance().get(Calendar.HOUR) + 1);
			time.set(Calendar.MINUTE, _newCycleMin);
			_log.info("FourSepulchersManager: Entry time: " + time.getTime());
			if (_firstTimeRun)
				_firstTimeRun = false; // Cooldown phase ends event hour, so it will be not first run
				
			long interval = time.getTimeInMillis() - System.currentTimeMillis();
			_changeEntryTimeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChangeEntryTime(), interval);
			
			if (_changeCoolDownTimeTask != null)
			{
				_changeCoolDownTimeTask.cancel(true);
				_changeCoolDownTimeTask = null;
			}
		}
	}
	
	public Map<Integer, Integer> getHallGateKeepers()
	{
		return _hallGateKeepers;
	}
	
	public void showHtmlFile(L2Player player, String file, L2Npc npc, L2Player member)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile("data/html/SepulcherNpc/" + file);
		if (member != null)
			html.replace("%member%", member.getName());
		player.sendPacket(html);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final FourSepulchersManager _instance = new FourSepulchersManager();
	}
}
