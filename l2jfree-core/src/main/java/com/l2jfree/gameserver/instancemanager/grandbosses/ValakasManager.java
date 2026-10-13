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
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.tools.random.Rnd;

/**
 * 
 * This class ... control for sequence of fight against Valakas.
 * 
 * @version $Revision: $ $Date: $
 * @author L2J_JP SANDMAN
 */
public class ValakasManager extends BossLair
{
	private static final class SingletonHolder
	{
		private static final ValakasManager INSTANCE = new ValakasManager();
	}
	
	public static ValakasManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public int _instanceId = 0;
	
	// Location of teleport cube.
	private final int _teleportCubeId = 31759;
	private final int _teleportCubeLocation[][] = { { 214880, -116144, -1644, 0 }, { 213696, -116592, -1644, 0 },
			{ 212112, -116688, -1644, 0 }, { 211184, -115472, -1664, 0 }, { 210336, -114592, -1644, 0 },
			{ 211360, -113904, -1644, 0 }, { 213152, -112352, -1644, 0 }, { 214032, -113232, -1644, 0 },
			{ 214752, -114592, -1644, 0 }, { 209824, -115568, -1421, 0 }, { 210528, -112192, -1403, 0 },
			{ 213120, -111136, -1408, 0 }, { 215184, -111504, -1392, 0 }, { 215456, -117328, -1392, 0 },
			{ 213200, -118160, -1424, 0 }
	
	};
	protected List<L2Spawn> _teleportCubeSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Npc> _teleportCube = new CopyOnWriteArrayList<L2Npc>();
	
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
	protected ScheduledFuture<?> _respawnValakasTask = null;
	
	public ValakasManager()
	{
		_questName = "valakas";
		_state = new GrandBossState(29028);
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
			
			// Valakas.
			template1 = NpcTable.getInstance().getTemplate(29028);
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(212852);
			tempSpawn.setLocy(-114842);
			tempSpawn.setLocz(-1632);
			//tempSpawn.setHeading(22106);
			tempSpawn.setHeading(833);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(Config.FWV_ACTIVITYTIMEOFVALAKAS * 2);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_monsterSpawn.put(29028, tempSpawn);
			
			// Dummy Valakas.
			template1 = NpcTable.getInstance().getTemplate(32123);
			tempSpawn = new L2Spawn(template1);
			tempSpawn.setLocx(212852);
			tempSpawn.setLocy(-114842);
			tempSpawn.setLocz(-1632);
			//tempSpawn.setHeading(22106);
			tempSpawn.setHeading(833);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(Config.FWV_ACTIVITYTIMEOFVALAKAS * 2);
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			_monsterSpawn.put(32123, tempSpawn);
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		// Setting spawn data of teleport cube.
		try
		{
			L2NpcTemplate Cube = NpcTable.getInstance().getTemplate(_teleportCubeId);
			L2Spawn spawnDat;
			for (int[] element : _teleportCubeLocation)
			{
				spawnDat = new L2Spawn(Cube);
				spawnDat.setAmount(1);
				spawnDat.setLocx(element[0]);
				spawnDat.setLocy(element[1]);
				spawnDat.setLocz(element[2]);
				spawnDat.setHeading(element[3]);
				spawnDat.setRespawnDelay(60);
				spawnDat.setLocation(0);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_teleportCubeSpawn.add(spawnDat);
			}
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		_log.info("ValakasManager : State of Valakas is " + _state.getState() + ".");
		if (_state.getState().equals(GrandBossState.StateEnum.ALIVE))
			restartValakas();
		else if (!_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN))
			setIntervalEndTask();
		
		Date dt = new Date(_state.getRespawnDate());
		_log.info("ValakasManager : Next spawn date of Valakas is " + dt + ".");
		_log.info("ValakasManager : Init ValakasManager.");
	}
	
	@Override
	public boolean isEnableEnterToLair()
	{
		return getPlayersInside().size() < Config.FWV_CAPACITYOFLAIR && super.isEnableEnterToLair();
	}
	
	// Do spawn teleport cube.
	public void spawnCube()
	{
		for (L2Spawn spawnDat : _teleportCubeSpawn)
		{
			spawnDat.setInstanceId(_instanceId);
			_teleportCube.add(spawnDat.doSpawn());
		}
	}
	
	// Setting Valakas spawn task.
	public void setValakasSpawnTask()
	{
		// When someone has already invaded the lair, nothing is done.
		if (getPlayersInside().size() >= 1)
			return;
		
		if (_monsterSpawnTask == null)
		{
			_monsterSpawnTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(1, null),
							Config.FWV_APPTIMEOFVALAKAS);
		}
	}
	
	// Do spawn Valakas.
	private class ValakasSpawn implements Runnable
	{
		private final int _distance = 6502500;
		private final int _taskId;
		private L2GrandBossInstance _valakas = null;
		private final List<L2Player> _players = getPlayersInside();
		
		ValakasSpawn(int taskId, L2GrandBossInstance valakas)
		{
			_taskId = taskId;
			_valakas = valakas;
		}
		
		@Override
		public void run()
		{
			SocialAction sa = null;
			
			switch (_taskId)
			{
				case 1:
					// Do spawn.
					L2Spawn valakasSpawn = _monsterSpawn.get(29028);
					valakasSpawn.setInstanceId(_instanceId);
					_valakas = (L2GrandBossInstance)valakasSpawn.doSpawn();
					_monsters.add(_valakas);
					_valakas.setIsImmobilized(true);
					_valakas.setIsInSocialAction(true);
					
					_state.setRespawnDate(Rnd.get(Config.FWV_FIXINTERVALOFVALAKAS, Config.FWV_FIXINTERVALOFVALAKAS
							+ Config.FWV_RANDOMINTERVALOFVALAKAS)
							+ Config.FWV_ACTIVITYTIMEOFVALAKAS);
					_state.setState(GrandBossState.StateEnum.ALIVE);
					_state.update();
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(2, _valakas), 16);
					
					break;
				
				case 2:
					// Do social.
					sa = new SocialAction(_valakas.getObjectId(), 1);
					_valakas.broadcastPacket(sa);
					
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 1800, 180, -1, 1500, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(3, _valakas), 1500);
					
					break;
				
				case 3:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 1300, 180, -5, 3000, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(4, _valakas), 3300);
					
					break;
				
				case 4:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 500, 180, -8, 600, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(5, _valakas), 1300);
					
					break;
				
				case 5:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 1200, 180, -5, 300, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(6, _valakas), 1600);
					
					break;
				
				case 6:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 2800, 250, 70, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(7, _valakas), 200);
					
					break;
				
				case 7:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 2600, 30, 60, 3400, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(8, _valakas), 5700);
					
					break;
				
				case 8:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 700, 150, -65, 0, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(9, _valakas), 1400);
					
					break;
				
				case 9:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 1200, 150, -55, 2900, 15000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(10, _valakas), 6700);
					
					break;
				
				case 10:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 750, 170, -10, 1700, 5700);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(11, _valakas), 3700);
					
					break;
				
				case 11:
					// Set camera.
					for (L2Player pc : _players)
					{
						if (pc.getPlanDistanceSq(_valakas) <= _distance)
						{
							pc.enterMovieMode();
							pc.specialCamera(_valakas, 840, 170, -5, 1200, 2000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// Set next task.
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(12, _valakas), 2000);
					
					break;
				
				case 12:
					// Reset camera.
					for (L2Player pc : _players)
					{
						pc.leaveMovieMode();
					}
					
					_mobiliseTask = ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(_valakas), 16);
					
					// Move at random.
					if (Config.FWV_MOVEATRANDOM)
					{
						L2CharPosition pos =
								new L2CharPosition(Rnd.get(211080, 214909), Rnd.get(-115841, -112822), -1662, 0);
						_moveAtRandomTask =
								ThreadPoolManager.getInstance().scheduleGeneral(new MoveAtRandom(_valakas, pos), 32);
					}
					
					// Set delete task.
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(),
									Config.FWV_ACTIVITYTIMEOFVALAKAS);
					
					break;
			}
		}
	}
	
	// At end of activity time.
	private class ActivityTimeEnd implements Runnable
	{
		@Override
		public void run()
		{
			setUnspawn();
		}
	}
	
	// Clean Valakas's lair.
	@Override
	public void setUnspawn()
	{
		// Eliminate players.
		banishForeigners();
		
		// Delete monsters.
		for (L2Npc mob : _monsters)
		{
			mob.getSpawn().stopRespawn();
			mob.deleteMe();
		}
		_monsters.clear();
		
		// Delete teleport cube.
		for (L2Npc cube : _teleportCube)
		{
			cube.getSpawn().stopRespawn();
			cube.deleteMe();
		}
		_teleportCube.clear();
		
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
		if (_respawnValakasTask != null)
		{
			_respawnValakasTask.cancel(true);
			_respawnValakasTask = null;
		}
		
		// Interval begin.
		setIntervalEndTask();
	}
	
	// Start interval.
	public void setIntervalEndTask()
	{
		//init state of Valakas's lair.
		if (!_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
		{
			_state.setRespawnDate(Rnd.get(Config.FWV_FIXINTERVALOFVALAKAS, Config.FWV_FIXINTERVALOFVALAKAS
					+ Config.FWV_RANDOMINTERVALOFVALAKAS));
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
		}
	}
	
	// Setting teleport cube spawn task.
	public void setCubeSpawn()
	{
		//init state of Valakas's lair.
		_state.setState(GrandBossState.StateEnum.DEAD);
		_state.update();
		
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
	
	// Move at random on after Valakas appears.
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
	
	// When a server restart while fight against Valakas.
	protected void restartValakas()
	{
		L2Spawn valakasSpawn = _monsterSpawn.get(32123);
		valakasSpawn.setInstanceId(_instanceId);
		L2Npc valakas = valakasSpawn.doSpawn();
		_monsters.add(valakas);
		
		// Set next task.
		if (_respawnValakasTask != null)
		{
			_respawnValakasTask.cancel(true);
			_respawnValakasTask = null;
		}
		_respawnValakasTask =
				ThreadPoolManager.getInstance().scheduleGeneral(new RestartValakas(valakas),
						Config.ALT_TIMELIMITOFINVADE + 1000);
	}
	
	private class RestartValakas implements Runnable
	{
		private final L2Npc _valakas;
		
		public RestartValakas(L2Npc valakas)
		{
			_valakas = valakas;
		}
		
		@Override
		public void run()
		{
			_valakas.getSpawn().stopRespawn();
			_valakas.deleteMe();
			
			// Set next task.
			if (_monsterSpawnTask != null)
			{
				_monsterSpawnTask.cancel(true);
				_monsterSpawnTask = null;
			}
			_monsterSpawnTask = ThreadPoolManager.getInstance().scheduleGeneral(new ValakasSpawn(1, null), 15000);
		}
	}
}
