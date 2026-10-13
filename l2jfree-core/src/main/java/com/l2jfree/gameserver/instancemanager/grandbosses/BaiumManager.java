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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.Earthquake;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.tools.random.Rnd;

/**
 * 
 * This class ... control for sequence of fight against Baium.
 * 
 * @version $Revision: $ $Date: $
 * @author L2J_JP SANDMAN
 */
public class BaiumManager extends BossLair
{
	private static final class SingletonHolder
	{
		private static final BaiumManager INSTANCE = new BaiumManager();
	}
	
	public static BaiumManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public int _instanceId = 0;
	
	public final static int BAIUM_NPC = 29025;
	public final static int BAIUM = 29020;
	public final static int ARCHANGEL = 29021;
	public final static int TELEPORT_CUBE = 29055;
	
	public final static int STATUE_LOCATION[] = { 115996, 17417, 10106, 41740 };
	protected L2Spawn _statueSpawn = null;
	
	// Location of arcangels.
	public final static int ANGEL_LOCATION[][] = { { 113004, 16209, 10076, 60242 }, { 114053, 16642, 10076, 4411 },
			{ 114563, 17184, 10076, 49241 }, { 116356, 16402, 10076, 31109 }, { 115015, 16393, 10076, 32760 },
			{ 115481, 15335, 10076, 16241 }, { 114680, 15407, 10051, 32485 }, { 114886, 14437, 10076, 16868 },
			{ 115391, 17593, 10076, 55346 }, { 115245, 17558, 10076, 35536 } };
	protected List<L2Spawn> _angelSpawns = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Npc> _angels = new CopyOnWriteArrayList<L2Npc>();
	
	// Location of teleport cube.
	public final static int CUBE_LOCATION[] = { 115203, 16620, 10078, 0 };
	protected L2Spawn _teleportCubeSpawn = null;
	protected L2Npc _teleportCube = null;
	
	// Instance of statue of Baium.
	protected L2Npc _npcBaium;
	
	// Spawn data of monsters.
	protected Map<Integer, L2Spawn> _monsterSpawn = new LinkedHashMap<Integer, L2Spawn>();
	
	// Instance of monsters.
	protected List<L2Npc> _monsters = new CopyOnWriteArrayList<L2Npc>();
	
	// Tasks.
	protected ScheduledFuture<?> _cubeSpawnTask = null;
	protected ScheduledFuture<?> _monsterSpawnTask = null;
	protected ScheduledFuture<?> _intervalEndTask = null;
	protected ScheduledFuture<?> _activityTimeEndTask = null;
	protected ScheduledFuture<?> _socialTask = null;
	protected ScheduledFuture<?> _mobiliseTask = null;
	protected ScheduledFuture<?> _moveAtRandomTask = null;
	protected ScheduledFuture<?> _socialTask2 = null;
	protected ScheduledFuture<?> _killPcTask = null;
	protected ScheduledFuture<?> _callAngelTask = null;
	protected ScheduledFuture<?> _sleepCheckTask = null;
	protected ScheduledFuture<?> _speakTask = null;
	
	// Status in lair.
	protected long _lastAttackTime = 0;
	protected String _words = "Don't obstruct my sleep! Die!";
	
	public BaiumManager()
	{
		_questName = "baium";
		_state = new GrandBossState(BAIUM);
	}
	
	// Initialize
	@Override
	public void init()
	{
		// Setting spawn data of monsters.
		try
		{
			L2NpcTemplate template1;
			L2Spawn tempSpawn;
			
			// Statue of Baium
			template1 = NpcTable.getInstance().getTemplate(BAIUM_NPC);
			_statueSpawn = new L2Spawn(template1);
			_statueSpawn.setAmount(1);
			_statueSpawn.setLocx(STATUE_LOCATION[0]);
			_statueSpawn.setLocy(STATUE_LOCATION[1]);
			_statueSpawn.setLocz(STATUE_LOCATION[2]);
			_statueSpawn.setHeading(STATUE_LOCATION[3]);
			_statueSpawn.setRespawnDelay(Config.FWB_ACTIVITYTIMEOFBAIUM * 2);
			_statueSpawn.stopRespawn();
			SpawnTable.getInstance().addNewSpawn(_statueSpawn, false);
			
			// Baium.
			template1 = NpcTable.getInstance().getTemplate(BAIUM);
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(Config.FWB_ACTIVITYTIMEOFBAIUM * 2);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_monsterSpawn.put(BAIUM, tempSpawn);
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		// Setting spawn data of teleport cube.
		try
		{
			L2NpcTemplate Cube = NpcTable.getInstance().getTemplate(TELEPORT_CUBE);
			L2Spawn spawnDat = new L2Spawn(Cube);
			spawnDat.setAmount(1);
			spawnDat.setLocx(CUBE_LOCATION[0]);
			spawnDat.setLocy(CUBE_LOCATION[1]);
			spawnDat.setLocz(CUBE_LOCATION[2]);
			spawnDat.setHeading(CUBE_LOCATION[3]);
			spawnDat.setRespawnDelay(60);
			spawnDat.setLocation(0);
			SpawnTable.getInstance().addNewSpawn(spawnDat, false);
			_teleportCubeSpawn = spawnDat;
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		// Setting spawn data of archangels.
		try
		{
			L2NpcTemplate angel = NpcTable.getInstance().getTemplate(ARCHANGEL);
			L2Spawn spawnDat;
			_angelSpawns.clear();
			
			// 5 random numbers of 10, no duplicates
			List<Integer> random = new ArrayList<Integer>();
			for (int i = 0; i < 5; i++)
			{
				int r = -1;
				while (r == -1 || random.contains(r))
					r = Rnd.get(10);
				random.add(r);
			}
			
			for (int i : random)
			{
				spawnDat = new L2Spawn(angel);
				spawnDat.setAmount(1);
				spawnDat.setLocx(ANGEL_LOCATION[i][0]);
				spawnDat.setLocy(ANGEL_LOCATION[i][1]);
				spawnDat.setLocz(ANGEL_LOCATION[i][2]);
				spawnDat.setHeading(ANGEL_LOCATION[i][3]);
				spawnDat.setRespawnDelay(300000);
				spawnDat.setLocation(0);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_angelSpawns.add(spawnDat);
			}
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		_log.info("BaiumManager : State of Baium is " + _state.getState() + ".");
		if (_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN))
			_statueSpawn.doSpawn();
		else if (_state.getState().equals(GrandBossState.StateEnum.ALIVE))
		{
			_state.setState(GrandBossState.StateEnum.NOTSPAWN);
			_state.update();
			_statueSpawn.doSpawn();
		}
		else if (_state.getState().equals(GrandBossState.StateEnum.INTERVAL)
				|| _state.getState().equals(GrandBossState.StateEnum.DEAD))
			setIntervalEndTask();
		
		Date dt = new Date(_state.getRespawnDate());
		_log.info("BaiumManager : Next spawn date of Baium is " + dt + ".");
		_log.info("BaiumManager : Init BaiumManager.");
	}
	
	// Archangel advent.
	protected void spawnArchangels()
	{
		for (L2Spawn spawn : _angelSpawns)
		{
			spawn.setInstanceId(_instanceId);
			_angels.add(spawn.doSpawn());
		}
	}
	
	// Archangel ascension.
	public void deleteArchangels()
	{
		for (L2Npc angel : _angels)
		{
			angel.getSpawn().stopRespawn();
			angel.deleteMe();
		}
		_angels.clear();
	}
	
	// Do spawn Baium.
	public void spawnBaium(L2Npc NpcBaium)
	{
		_npcBaium = NpcBaium;
		
		// Get target from statue,to kill a player of make Baium awake.
		L2Player target = (L2Player)_npcBaium.getTarget();
		
		// Do spawn.
		L2Spawn baiumSpawn = _monsterSpawn.get(BAIUM);
		baiumSpawn.setLocx(_npcBaium.getX());
		baiumSpawn.setLocy(_npcBaium.getY());
		baiumSpawn.setLocz(_npcBaium.getZ());
		baiumSpawn.setHeading(_npcBaium.getHeading());
		
		baiumSpawn.setInstanceId(_instanceId);
		
		// Delete statue.
		_npcBaium.deleteMe();
		
		final L2GrandBossInstance baium = (L2GrandBossInstance)baiumSpawn.doSpawn();
		_monsters.add(baium);
		
		_state.setRespawnDate(Rnd.get(Config.FWB_FIXINTERVALOFBAIUM, Config.FWB_FIXINTERVALOFBAIUM
				+ Config.FWB_RANDOMINTERVALOFBAIUM)
				+ Config.FWB_ACTIVITYTIMEOFBAIUM);
		_state.setState(GrandBossState.StateEnum.ALIVE);
		_state.update();
		
		// Set last attack time.
		setLastAttackTime();
		
		// Stop respawn of statue.
		_npcBaium.getSpawn().stopRespawn();
		
		// Do social.
		baium.setIsImmobilized(true);
		baium.setIsInSocialAction(true);
		
		SocialAction sa = new SocialAction(baium.getObjectId(), 2);
		baium.broadcastPacket(sa);
		
		_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new Social(baium, 3), 15000);
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				Earthquake eq = new Earthquake(baium.getX(), baium.getY(), baium.getZ(), 40, 5);
				baium.broadcastPacket(eq);
			}
		}, 25000);
		
		_socialTask2 = ThreadPoolManager.getInstance().scheduleGeneral(new Social(baium, 1), 25000);
		
		_killPcTask = ThreadPoolManager.getInstance().scheduleGeneral(new KillPc(target, baium), 26000);
		
		_callAngelTask = ThreadPoolManager.getInstance().scheduleGeneral(new CallArchAngel(), 35000);
		
		_mobiliseTask = ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(baium), 35500);
		
		// Move at random.
		if (Config.FWB_MOVEATRANDOM)
		{
			L2CharPosition pos = new L2CharPosition(Rnd.get(112826, 116241), Rnd.get(15575, 16375), 10078, 0);
			_moveAtRandomTask = ThreadPoolManager.getInstance().scheduleGeneral(new MoveAtRandom(baium, pos), 36000);
		}
		
		// Set delete task.
		_activityTimeEndTask =
				ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(), Config.FWB_ACTIVITYTIMEOFBAIUM);
		_sleepCheckTask = ThreadPoolManager.getInstance().scheduleGeneral(new CheckLastAttack(), 60000);
	}
	
	// At end of activity time.
	private class ActivityTimeEnd implements Runnable
	{
		@Override
		public void run()
		{
			if (_state.getState().equals(GrandBossState.StateEnum.DEAD))
				setIntervalEndTask();
			else
				sleepBaium();
		}
	}
	
	// Clean Baium's lair.
	@Override
	public void setUnspawn()
	{
		// Eliminate players.
		banishForeigners();
		
		// Delete monsters.
		deleteArchangels();
		for (L2Npc mob : _monsters)
		{
			mob.getSpawn().stopRespawn();
			mob.deleteMe();
		}
		_monsters.clear();
		
		// Delete teleport cube.
		if (_teleportCube != null)
		{
			_teleportCube.getSpawn().stopRespawn();
			_teleportCube.deleteMe();
			_teleportCube = null;
		}
		
		// Not executed tasks is canceled.
		if (_cubeSpawnTask != null)
		{
			_cubeSpawnTask.cancel(true);
			_cubeSpawnTask = null;
		}
		if (_monsterSpawnTask != null)
		{
			_monsterSpawnTask.cancel(true);
			_monsterSpawnTask = null;
		}
		if (_intervalEndTask != null)
		{
			_intervalEndTask.cancel(true);
			_intervalEndTask = null;
		}
		if (_activityTimeEndTask != null)
		{
			_activityTimeEndTask.cancel(true);
			_activityTimeEndTask = null;
		}
		if (_socialTask != null)
		{
			_socialTask.cancel(true);
			_socialTask = null;
		}
		if (_mobiliseTask != null)
		{
			_mobiliseTask.cancel(true);
			_mobiliseTask = null;
		}
		if (_moveAtRandomTask != null)
		{
			_moveAtRandomTask.cancel(true);
			_moveAtRandomTask = null;
		}
		if (_socialTask2 != null)
		{
			_socialTask2.cancel(true);
			_socialTask2 = null;
		}
		if (_killPcTask != null)
		{
			_killPcTask.cancel(true);
			_killPcTask = null;
		}
		if (_callAngelTask != null)
		{
			_callAngelTask.cancel(true);
			_callAngelTask = null;
		}
		if (_sleepCheckTask != null)
		{
			_sleepCheckTask.cancel(true);
			_sleepCheckTask = null;
		}
		if (_speakTask != null)
		{
			_speakTask.cancel(true);
			_speakTask = null;
		}
	}
	
	// Do spawn teleport cube.
	public void spawnCube()
	{
		_teleportCubeSpawn.setInstanceId(_instanceId);
		_teleportCube = _teleportCubeSpawn.doSpawn();
	}
	
	// Start interval.
	public void setIntervalEndTask()
	{
		setUnspawn();
		
		//init state of Baium's lair.
		if (!_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
		{
			_state.setRespawnDate(Rnd.get(Config.FWB_FIXINTERVALOFBAIUM, Config.FWB_FIXINTERVALOFBAIUM
					+ Config.FWB_RANDOMINTERVALOFBAIUM));
			_state.setState(GrandBossState.StateEnum.INTERVAL);
			_state.update();
		}
		
		_intervalEndTask = ThreadPoolManager.getInstance().scheduleGeneral(new IntervalEnd(), _state.getInterval());
	}
	
	// At end of interval.
	private class IntervalEnd implements Runnable
	{
		@Override
		public void run()
		{
			_state.setState(GrandBossState.StateEnum.NOTSPAWN);
			_state.update();
			
			// statue of Baium respawn.
			_statueSpawn.doSpawn();
		}
	}
	
	// Setting teleport cube spawn task.
	public void setCubeSpawn()
	{
		_state.setState(GrandBossState.StateEnum.DEAD);
		_state.update();
		
		deleteArchangels();
		
		_cubeSpawnTask = ThreadPoolManager.getInstance().scheduleGeneral(new CubeSpawn(), 10000);
	}
	
	// Do spawn teleport cube.
	private class CubeSpawn implements Runnable
	{
		@Override
		public void run()
		{
			spawnCube();
		}
	}
	
	// Do social.
	private class Social implements Runnable
	{
		private final int _action;
		private final L2Npc _npc;
		
		public Social(L2Npc npc, int actionId)
		{
			_npc = npc;
			_action = actionId;
		}
		
		@Override
		public void run()
		{
			SocialAction sa = new SocialAction(_npc.getObjectId(), _action);
			_npc.broadcastPacket(sa);
		}
	}
	
	// Action is enabled the boss.
	private class SetMobilised implements Runnable
	{
		private final L2GrandBossInstance _boss;
		
		public SetMobilised(L2GrandBossInstance boss)
		{
			_boss = boss;
		}
		
		@Override
		public void run()
		{
			_boss.setIsImmobilized(false);
			_boss.setIsInSocialAction(false);
			
			// When it is possible to act, a social action is canceled.
			if (_socialTask != null)
			{
				_socialTask.cancel(true);
				_socialTask = null;
			}
		}
	}
	
	// Move at random on after Baium appears.
	private class MoveAtRandom implements Runnable
	{
		private final L2Npc _npc;
		private final L2CharPosition _pos;
		
		public MoveAtRandom(L2Npc npc, L2CharPosition pos)
		{
			_npc = npc;
			_pos = pos;
		}
		
		@Override
		public void run()
		{
			_npc.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, _pos);
		}
	}
	
	// Call Arcangels
	private class CallArchAngel implements Runnable
	{
		@Override
		public void run()
		{
			spawnArchangels();
		}
	}
	
	// Kill pc
	private class KillPc implements Runnable
	{
		private final L2Player _target;
		private final L2GrandBossInstance _boss;
		
		public KillPc(L2Player target, L2GrandBossInstance boss)
		{
			_target = target;
			_boss = boss;
		}
		
		@Override
		public void run()
		{
			L2Skill skill = SkillTable.getInstance().getInfo(4136, 1);
			if (_target != null && skill != null)
			{
				_boss.setTarget(_target);
				_boss.doCast(skill);
			}
		}
	}
	
	// Baium sleeps if not attacked for 30 minutes.
	public void sleepBaium()
	{
		setUnspawn();
		_state.setState(GrandBossState.StateEnum.NOTSPAWN);
		_state.update();
		
		// statue of Baium respawn.
		_statueSpawn.doSpawn();
		
		checkAnnihilated();
	}
	
	public void setLastAttackTime()
	{
		_lastAttackTime = System.currentTimeMillis();
	}
	
	private class CheckLastAttack implements Runnable
	{
		@Override
		public void run()
		{
			if (_state.getState().equals(GrandBossState.StateEnum.ALIVE))
			{
				if (_lastAttackTime + Config.FWB_LIMITUNTILSLEEP < System.currentTimeMillis())
					sleepBaium();
				else
				{
					if (_sleepCheckTask != null)
					{
						_sleepCheckTask.cancel(true);
						_sleepCheckTask = null;
					}
					_sleepCheckTask = ThreadPoolManager.getInstance().scheduleGeneral(new CheckLastAttack(), 60000);
				}
			}
		}
	}
	
	public void spawnStatue()
	{
		_statueSpawn.setInstanceId(_instanceId);
		_statueSpawn.doSpawn();
	}
}
