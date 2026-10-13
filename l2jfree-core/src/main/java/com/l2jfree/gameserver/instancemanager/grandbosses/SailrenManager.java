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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * 
 * This class ...
 * Management for fight with sailren.
 * @version $Revision: $ $Date: $
 * @author  L2J_JP SANDMAN
 */
public class SailrenManager extends BossLair
{
	private static final class SingletonHolder
	{
		private static final SailrenManager INSTANCE = new SailrenManager();
	}
	
	public static SailrenManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	// Teleport cube location.
	private final int _sailrenCubeLocation[][] = { { 27734, -6838, -1982, 0 } };
	protected List<L2Spawn> _sailrenCubeSpawn = new CopyOnWriteArrayList<L2Spawn>();
	protected List<L2Npc> _sailrenCube = new CopyOnWriteArrayList<L2Npc>();
	
	// Spawn data of monsters
	protected L2Spawn _velociraptorSpawn; // Velociraptor
	protected L2Spawn _pterosaurSpawn; // Pterosaur
	protected L2Spawn _tyrannoSpawn; // Tyrannosaurus
	protected L2Spawn _sailrenSapwn; // Sailren
	
	// Instance of monsters
	protected L2Npc _velociraptor; // Velociraptor
	protected L2Npc _pterosaur; // Pterosaur
	protected L2Npc _tyranno; // Tyrannosaurus
	protected L2Npc _sailren; // Sailren
	
	// Tasks
	protected ScheduledFuture<?> _cubeSpawnTask = null;
	protected ScheduledFuture<?> _sailrenSpawnTask = null;
	protected ScheduledFuture<?> _intervalEndTask = null;
	protected ScheduledFuture<?> _activityTimeEndTask = null;
	protected ScheduledFuture<?> _onPartyAnnihilatedTask = null;
	protected ScheduledFuture<?> _socialTask = null;
	
	// State of Sailren's lair.
	protected boolean _isAlreadyEnteredOtherParty = false;
	
	public SailrenManager()
	{
		_questName = "sailren";
		_state = new GrandBossState(29065);
	}
	
	// Init.
	@Override
	public void init()
	{
		// Init state.
		_isAlreadyEnteredOtherParty = false;
		
		// Setting spawn data of monsters.
		try
		{
			L2NpcTemplate template1;
			
			// Velociraptor
			template1 = NpcTable.getInstance().getTemplate(22218); //Velociraptor
			_velociraptorSpawn = new L2Spawn(template1);
			_velociraptorSpawn.setLocx(27852);
			_velociraptorSpawn.setLocy(-5536);
			_velociraptorSpawn.setLocz(-1983);
			_velociraptorSpawn.setHeading(44732);
			_velociraptorSpawn.setAmount(1);
			_velociraptorSpawn.setRespawnDelay(Config.FWS_ACTIVITYTIMEOFMOBS * 2);
			SpawnTable.getInstance().addNewSpawn(_velociraptorSpawn, false);
			
			// Pterosaur
			template1 = NpcTable.getInstance().getTemplate(22199); //Pterosaur
			_pterosaurSpawn = new L2Spawn(template1);
			_pterosaurSpawn.setLocx(27852);
			_pterosaurSpawn.setLocy(-5536);
			_pterosaurSpawn.setLocz(-1983);
			_pterosaurSpawn.setHeading(44732);
			_pterosaurSpawn.setAmount(1);
			_pterosaurSpawn.setRespawnDelay(Config.FWS_ACTIVITYTIMEOFMOBS * 2);
			SpawnTable.getInstance().addNewSpawn(_pterosaurSpawn, false);
			
			// Tyrannosaurus
			template1 = NpcTable.getInstance().getTemplate(22217); //Tyrannosaurus
			_tyrannoSpawn = new L2Spawn(template1);
			_tyrannoSpawn.setLocx(27852);
			_tyrannoSpawn.setLocy(-5536);
			_tyrannoSpawn.setLocz(-1983);
			_tyrannoSpawn.setHeading(44732);
			_tyrannoSpawn.setAmount(1);
			_tyrannoSpawn.setRespawnDelay(Config.FWS_ACTIVITYTIMEOFMOBS * 2);
			SpawnTable.getInstance().addNewSpawn(_tyrannoSpawn, false);
			
			// Sailren
			template1 = NpcTable.getInstance().getTemplate(29065); //Sailren
			_sailrenSapwn = new L2Spawn(template1);
			_sailrenSapwn.setLocx(27810);
			_sailrenSapwn.setLocy(-5655);
			_sailrenSapwn.setLocz(-1983);
			_sailrenSapwn.setHeading(44732);
			_sailrenSapwn.setAmount(1);
			_sailrenSapwn.setRespawnDelay(Config.FWS_ACTIVITYTIMEOFMOBS * 2);
			SpawnTable.getInstance().addNewSpawn(_sailrenSapwn, false);
			
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		// Setting spawn data of teleporte cube.
		try
		{
			L2NpcTemplate cube = NpcTable.getInstance().getTemplate(32107);
			L2Spawn spawnDat;
			
			for (int[] element : _sailrenCubeLocation)
			{
				spawnDat = new L2Spawn(cube);
				spawnDat.setAmount(1);
				spawnDat.setLocx(element[0]);
				spawnDat.setLocy(element[1]);
				spawnDat.setLocz(element[2]);
				spawnDat.setHeading(element[3]);
				spawnDat.setRespawnDelay(60);
				spawnDat.setLocation(0);
				SpawnTable.getInstance().addNewSpawn(spawnDat, false);
				_sailrenCubeSpawn.add(spawnDat);
			}
		}
		catch (Exception e)
		{
			BossInit.failed(_log, e);
		}
		
		_log.info("SailrenManager : State of Sailren is " + _state.getState() + ".");
		if (!_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN))
			setIntervalEndTask();
		
		Date dt = new Date(_state.getRespawnDate());
		_log.info("SailrenManager : Next spawn date of Sailren is " + dt + ".");
		_log.info("SailrenManager : Init SailrenManager.");
	}
	
	// Whether it is permitted to enter the sailren's lair is confirmed.
	public int canIntoSailrenLair(L2Player pc)
	{
		if ((!Config.FWS_ENABLESINGLEPLAYER) && (pc.getParty() == null))
			return 4;
		else if (_isAlreadyEnteredOtherParty)
			return 2;
		else if (_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN))
			return 0;
		else if (_state.getState().equals(GrandBossState.StateEnum.ALIVE)
				|| _state.getState().equals(GrandBossState.StateEnum.DEAD))
			return 1;
		else if (_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
			return 3;
		else
			return 0;
		
	}
	
	// Set Sailren spawn task.
	public void setSailrenSpawnTask(int npcId)
	{
		if ((npcId == 22218) && (getPlayersInside().size() >= 1))
			return;
		
		if (_sailrenSpawnTask == null)
		{
			_sailrenSpawnTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new SailrenSpawn(npcId),
							Config.FWS_INTERVALOFNEXTMONSTER);
		}
	}
	
	// Teleporting player to sailren's lair.
	public void entryToSailrenLair(L2Player pc)
	{
		int driftx;
		int drifty;
		
		if (canIntoSailrenLair(pc) != 0)
		{
			pc.sendMessage("Entrance was refused because it did not satisfy it.");
			_isAlreadyEnteredOtherParty = false;
			return;
		}
		
		if (pc.getParty() == null)
		{
			driftx = Rnd.get(-80, 80);
			drifty = Rnd.get(-80, 80);
			pc.teleToLocation(27734 + driftx, -6938 + drifty, -1982);
		}
		else
		{
			List<L2Player> members = new ArrayList<L2Player>(); // list of member of teleport candidate.
			for (L2Player mem : pc.getParty().getPartyMembers())
			{
				// teleporting it within alive and the range of recognition of the leader of the party.
				if (!mem.isDead() && Util.checkIfInRange(700, pc, mem, true))
				{
					members.add(mem);
				}
			}
			for (L2Player mem : members)
			{
				driftx = Rnd.get(-80, 80);
				drifty = Rnd.get(-80, 80);
				mem.teleToLocation(27734 + driftx, -6938 + drifty, -1982);
			}
		}
		_isAlreadyEnteredOtherParty = true;
	}
	
	// When annihilating or limit of time coming, the compulsion movement players from the sailren's lair.
	@Override
	public void banishForeigners()
	{
		super.banishForeigners();
		_isAlreadyEnteredOtherParty = false;
	}
	
	// Clean up Sailren's lair.
	@Override
	public void setUnspawn()
	{
		// Eliminate players.
		banishForeigners();
		
		// Delete teleport cube.
		for (L2Npc cube : _sailrenCube)
		{
			cube.getSpawn().stopRespawn();
			cube.deleteMe();
		}
		_sailrenCube.clear();
		
		// Not executed tasks is canceled.
		if (_cubeSpawnTask != null)
		{
			_cubeSpawnTask.cancel(true);
			_cubeSpawnTask = null;
		}
		if (_sailrenSpawnTask != null)
		{
			_sailrenSpawnTask.cancel(true);
			_sailrenSpawnTask = null;
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
		
		// Init state of sailren's lair.
		_velociraptor = null;
		_pterosaur = null;
		_tyranno = null;
		_sailren = null;
		
		// Interval begin.
		setIntervalEndTask();
	}
	
	// Spawn teleport cube.
	public void spawnCube()
	{
		for (L2Spawn spawnDat : _sailrenCubeSpawn)
		{
			_sailrenCube.add(spawnDat.doSpawn());
		}
	}
	
	// Task of teleport cube spawn.
	public void setCubeSpawn()
	{
		_state.setState(GrandBossState.StateEnum.DEAD);
		_state.update();
		
		_cubeSpawnTask = ThreadPoolManager.getInstance().scheduleGeneral(new CubeSpawn(), 10000);
		
	}
	
	// Task of interval of sailren spawn.
	public void setIntervalEndTask()
	{
		if (!_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
		{
			_state.setRespawnDate(Rnd.get(Config.FWS_FIXINTERVALOFSAILRENSPAWN, Config.FWS_FIXINTERVALOFSAILRENSPAWN
					+ Config.FWS_RANDOMINTERVALOFSAILRENSPAWN));
			_state.setState(GrandBossState.StateEnum.INTERVAL);
			_state.update();
		}
		
		_intervalEndTask = ThreadPoolManager.getInstance().scheduleGeneral(new IntervalEnd(), _state.getInterval());
	}
	
	// Spawn monster.
	private class SailrenSpawn implements Runnable
	{
		private final int _npcId;
		private final L2CharPosition _pos = new L2CharPosition(27628, -6109, -1982, 44732);
		
		public SailrenSpawn(int npcId)
		{
			_npcId = npcId;
		}
		
		@Override
		public void run()
		{
			switch (_npcId)
			{
				case 22218: // Velociraptor
					_velociraptor = _velociraptorSpawn.doSpawn();
					_velociraptor.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, _pos);
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new Social(_velociraptor, 2), 6000);
					if (_activityTimeEndTask != null)
					{
						_activityTimeEndTask.cancel(true);
						_activityTimeEndTask = null;
					}
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(_velociraptor),
									Config.FWS_ACTIVITYTIMEOFMOBS);
					break;
				case 22199: // Pterosaur
					_velociraptorSpawn.stopRespawn();
					_pterosaur = _pterosaurSpawn.doSpawn();
					_pterosaur.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, _pos);
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new Social(_pterosaur, 2), 6000);
					if (_activityTimeEndTask != null)
					{
						_activityTimeEndTask.cancel(true);
						_activityTimeEndTask = null;
					}
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(_pterosaur),
									Config.FWS_ACTIVITYTIMEOFMOBS);
					break;
				case 22217: // Tyrannosaurus
					_pterosaurSpawn.stopRespawn();
					_tyranno = _tyrannoSpawn.doSpawn();
					_tyranno.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, _pos);
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new Social(_tyranno, 2), 6000);
					if (_activityTimeEndTask != null)
					{
						_activityTimeEndTask.cancel(true);
						_activityTimeEndTask = null;
					}
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(_tyranno),
									Config.FWS_ACTIVITYTIMEOFMOBS);
					break;
				case 29065: // Sailren
					_tyrannoSpawn.stopRespawn();
					_sailren = _sailrenSapwn.doSpawn();
					
					_state.setRespawnDate(Rnd.get(Config.FWS_FIXINTERVALOFSAILRENSPAWN,
							Config.FWS_FIXINTERVALOFSAILRENSPAWN + Config.FWS_RANDOMINTERVALOFSAILRENSPAWN)
							+ Config.FWS_ACTIVITYTIMEOFMOBS);
					_state.setState(GrandBossState.StateEnum.ALIVE);
					_state.update();
					
					_sailren.getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, _pos);
					if (_socialTask != null)
					{
						_socialTask.cancel(true);
						_socialTask = null;
					}
					_socialTask = ThreadPoolManager.getInstance().scheduleGeneral(new Social(_sailren, 2), 6000);
					if (_activityTimeEndTask != null)
					{
						_activityTimeEndTask.cancel(true);
						_activityTimeEndTask = null;
					}
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(_sailren),
									Config.FWS_ACTIVITYTIMEOFMOBS);
					break;
			}
			
			if (_sailrenSpawnTask != null)
			{
				_sailrenSpawnTask.cancel(true);
				_sailrenSpawnTask = null;
			}
		}
	}
	
	// Spawn teleport cube.
	private class CubeSpawn implements Runnable
	{
		@Override
		public void run()
		{
			spawnCube();
		}
	}
	
	// Limit of time coming.
	private class ActivityTimeEnd implements Runnable
	{
		private L2Npc _mob;
		
		public ActivityTimeEnd(L2Npc npc)
		{
			_mob = npc;
		}
		
		@Override
		public void run()
		{
			if (!_mob.isDead())
			{
				_mob.deleteMe();
				_mob.getSpawn().stopRespawn();
				_mob = null;
			}
			// clean up sailren's lair.
			setUnspawn();
		}
	}
	
	// Interval end.
	private class IntervalEnd implements Runnable
	{
		@Override
		public void run()
		{
			_state.setState(GrandBossState.StateEnum.NOTSPAWN);
			_state.update();
		}
	}
	
	// Social.
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
}
