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
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.funcs.FuncMul;
import com.l2jfree.gameserver.model.skills.funcs.FuncOwner;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.Earthquake;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.tools.random.Rnd;

/** *************************- main class + global class values -*********************************** */

/**
 * control for sequence of Frintezza and Scarlet Van Halisha and their minions.
 * 
 * @version 1.00
 * @author Darki699
 */

public class FrintezzaManager extends BossLair
{
	private static final class SingletonHolder
	{
		private static final FrintezzaManager INSTANCE = new FrintezzaManager();
	}
	
	public static FrintezzaManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	// Must be done this way :( Each of the 11 mobs is an individual with different tasks and values
	// So there is no point to place them in an array
	private L2Spawn frintezzaSpawn, scarletSpawnWeak, scarletSpawnStrong, portraitSpawn1, portraitSpawn2,
			portraitSpawn3, portraitSpawn4, demonSpawn1, demonSpawn2, demonSpawn3, demonSpawn4;
	
	private L2GrandBossInstance weakScarlet, strongScarlet, portrait1, portrait2, portrait3, portrait4;
	private L2Npc frintezza;
	
	// The minions be used as L2MonsterInstance, instead of L2MinionInstance, since they
	// have 3 Bosses: weak scarlet, strong scarlet, and frintezza. All 3 bosses control
	// the minions. Also we do not want the portraits to respawn next to the boss,
	// and we need different respawn intervals for
	// demons and portraits.
	private L2MonsterInstance demon1, demon2, demon3, demon4;
	
	// Interval time of Monsters.
	protected int _intervalOfBoss, _intervalOfDemons, _intervalOfRetarget, _intervalOfFrintezzaSongs,
			_callForHelpInterval;
	
	// Delay of appearance time of Boss.
	protected int _appTimeOfBoss;
	
	// Activity time of Boss.
	protected int _activityTimeOfBoss;
	
	// lists of last saved positions <objectId, location>
	protected Map<Integer, Point3D> _lastLocation = new LinkedHashMap<Integer, Point3D>();
	
	// status in lair.
	protected boolean _respawningDemon1 = false, _respawningDemon2 = false, _respawningDemon3 = false,
			_respawningDemon4 = false, _scarletIsWeakest = true;
	
	protected ScheduledFuture<?> _monsterSpawnTask = null;
	protected ScheduledFuture<?> _activityTimeEndTask = null;
	protected ScheduledFuture<?> _intervalEndTask = null;
	
	private Func _DecreaseRegHp = null;
	private int _debuffPeriod = 0;
	
	/** ************************************ Initial Functions ************************************* */
	
	/**
	 * Empty constructor Does nothing
	 */
	public FrintezzaManager()
	{
		_questName = "frintezza";
		_state = new GrandBossState(29045);
	}
	
	/**
	 * initialize <b>this</b> Frintezza Manager
	 */
	@Override
	public void init()
	{
		_callForHelpInterval = 2000;
		
		_intervalOfRetarget = 10000;
		
		_intervalOfFrintezzaSongs = 30000;
		
		_intervalOfDemons = Config.FWF_INTERVALOFNEXTMONSTER;
		_intervalOfBoss = Config.FWF_INTERVALOFFRINTEZZA;
		_appTimeOfBoss = Config.FWF_INTERVALOFNEXTMONSTER;
		_activityTimeOfBoss = Config.FWF_ACTIVITYTIMEOFFRINTEZZA;
		
		// initialize status in lair.
		_scarletIsWeakest = true;
		
		// setting spawn data of monsters.
		try
		{
			createMonsterSpawns();
		}
		catch (RuntimeException e)
		{
			BossInit.failed(_log, e);
		}
		
		_log.info("FrintezzaManager : State of Frintezza is " + _state.getState() + ".");
		if (!_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN))
			setIntervalEndTask();
		
		Date dt = new Date(_state.getRespawnDate());
		_log.info("FrintezzaManager : Next spawn date of Frintezza is " + dt + ".");
		_log.info("FrintezzaManager : Init FrintezzaManager.");
	}
	
	/** ***************************** Spawn Manager Creates all spawns ******************************** */
	
	/**
	 * Makes a spawn of all monsters, but doe not spawn them yet
	 */
	private void createMonsterSpawns()
	{
		// The boss of all bosses ofcourse
		frintezzaSpawn = createNewSpawn(29045, 174240, -89805, -5022, 16048, _intervalOfBoss);
		
		// weak Scarlet Van Halisha.
		scarletSpawnWeak = createNewSpawn(29046, 147725, -56517, -2780, 48028, _intervalOfBoss);
		
		// Strong Scarlet Van Halisha -> x , y , z , heading, and Hp are set when the morph actually
		// happens.
		scarletSpawnStrong = createNewSpawn(29047, 174234, -88015, -5116, 48028, _intervalOfBoss);
		
		// Portrait spawns - 4 portraits = 4 spawns
		portraitSpawn1 = createNewSpawn(29048, 175833, -87165, -4972, 35048, _intervalOfBoss);
		
		portraitSpawn2 = createNewSpawn(29049, 175876, -88713, -4972, 28205, _intervalOfBoss);
		
		portraitSpawn3 = createNewSpawn(29048, 172608, -88702, -4972, 64817, _intervalOfBoss);
		
		portraitSpawn4 = createNewSpawn(29049, 172634, -87165, -4972, 57730, _intervalOfBoss);
		
		// Demon spawns - 4 portraits = 4 demons (?)
		demonSpawn1 = createNewSpawn(29050, 175833, -87165, -4972, 35048, _intervalOfDemons);
		
		demonSpawn2 = createNewSpawn(29051, 175876, -88713, -4972, 28205, _intervalOfDemons);
		
		demonSpawn3 = createNewSpawn(29051, 172608, -88702, -4972, 64817, _intervalOfDemons);
		
		demonSpawn4 = createNewSpawn(29050, 172634, -87165, -4972, 57730, _intervalOfDemons);
	}
	
	/**
	 * Creates a single spawn from the parameter values and returns the L2Spawn created
	 * 
	 * @param templateId
	 *            int value of the monster template id number
	 * @param x
	 *            int value of the X position
	 * @param y
	 *            int value of the Y position
	 * @param z
	 *            int value of the Z position
	 * @param heading
	 *            int value of where is the monster facing to...
	 * @param respawnDelay
	 *            int value of the respawn of this L2Spawn
	 * @return L2Spawn created
	 */
	private L2Spawn createNewSpawn(int templateId, int x, int y, int z, int heading, int respawnDelay)
	{
		L2Spawn tempSpawn = null;
		
		L2NpcTemplate template1;
		
		try
		{
			template1 = NpcTable.getInstance().getTemplate(templateId);
			if (template1 == null)
				return null;
			tempSpawn = new L2Spawn(template1);
			
			tempSpawn.setLocx(x);
			tempSpawn.setLocy(y);
			tempSpawn.setLocz(z);
			tempSpawn.setHeading(heading);
			tempSpawn.setAmount(1);
			tempSpawn.setRespawnDelay(respawnDelay);
			tempSpawn.stopRespawn();
			
			SpawnTable.getInstance().addNewSpawn(tempSpawn, false);
			
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		return tempSpawn;
	}
	
	/** ************************ Starting the battle with Frintezza + co. ***************************** */
	
	/**
	 * setting Scarlet Van Halisha spawn task which also starts the whole Frintezza battle.
	 */
	public void setScarletSpawnTask()
	{
		if (_state.getState().equals(GrandBossState.StateEnum.NOTSPAWN) && _monsterSpawnTask == null)
		{
			_monsterSpawnTask =
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(1), _appTimeOfBoss);
		}
	}
	
	/**
	 * Shows a movie to the players in the lair.
	 * 
	 * @param target -
	 *            L2Npc target is the center of this movie
	 * @param dist -
	 *            int distance from target
	 * @param yaw -
	 *            angle of movie (north = 90, south = 270, east = 0 , west = 180)
	 * @param pitch -
	 *            pitch > 0 looks up / pitch < 0 looks down
	 * @param time -
	 *            fast ++ or slow -- depends on the value
	 * @param duration -
	 *            How long to watch the movie
	 * @param socialAction -
	 *            1,2,3 social actions / other values do nothing
	 */
	
	private void showSocialActionMovie(L2Npc target, int dist, int yaw, int pitch, int time, int duration,
			int socialAction)
	{
		if (target == null)
			return;
		
		// set camera.
		for (L2Player pc : getPlayersInside())
		{
			
			setIdle(pc);
			
			pc.setTarget(null);
			
			if (pc.getPlanDistanceSq(target) <= 6502500)
			{
				pc.enterMovieMode();
				pc.specialCamera(target, dist, yaw, pitch, time, duration);
			}
			else
			{
				pc.leaveMovieMode();
			}
		}
		
		// do social.
		if (socialAction > 0 && socialAction < 5)
		{
			target.broadcastPacket(new SocialAction(target.getObjectId(), socialAction));
		}
	}
	
	/**
	 * I noticed that if the players do not stand at a certain position, they can not watch the entire movie, so I set them to the center during the entire
	 * movie.
	 */
	
	private void teleportToStart()
	{
		if (_lastLocation == null)
			_lastLocation = new LinkedHashMap<Integer, Point3D>();
		
		Point3D p = new Point3D(174233, -88212, -5116);
		
		for (L2Player pc : getPlayersInside())
		{
			if (pc.getX() != p.getX() && pc.getY() != p.getY() && pc.getZ() != p.getZ())
			{
				if (!_lastLocation.containsKey(pc.getObjectId()))
					_lastLocation.put(pc.getObjectId(), new Point3D(pc.getX(), pc.getY(), pc.getZ()));
				
				pc.teleToLocation(p.getX(), p.getY(), p.getZ());
			}
			
		}
		
	}
	
	/**
	 * Teleports the players back to their last positions before the movie started
	 */
	
	private void teleportToFinish()
	{
		if (_lastLocation == null || _lastLocation.isEmpty())
			return;
		
		for (L2Player pc : getPlayersInside())
		{
			if (_lastLocation.containsKey(pc.getObjectId()))
			{
				Point3D loc = _lastLocation.get(pc.getObjectId());
				pc.teleToLocation(loc.getX(), loc.getY(), loc.getZ());
			}
		}
		_lastLocation.clear();
	}
	
	/** ************************** Initialize a movie and spawn the monsters *********************** */
	
	/**
	 * Spawns Frintezza, the weak version of Scarlet Van Halisha, the minions, and all that is shown in a movie to the observing players.
	 */
	
	private class ScarletWeakSpawn implements Runnable
	{
		private int _taskId = 0;
		
		public ScarletWeakSpawn(int taskId)
		{
			_taskId = taskId;
		}
		
		@Override
		public void run()
		{
			switch (_taskId)
			{
			
				case 1: // spawn.
					frintezza = frintezzaSpawn.doSpawn();
					frintezza.setIsImmobilized(true);
					frintezza.disableAllSkills();
					
					_state.setRespawnDate(_intervalOfBoss);
					_state.setState(GrandBossState.StateEnum.ALIVE);
					_state.update();
					
					teleportToStart();
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(2), 1000);
					
					break;
				
				case 2:
					// show movie
					showSocialActionMovie(frintezza, 1000, 90, 30, 0, 5000, 0);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(200), 3000);
					
					break;
				
				case 200:
					// show movie
					showSocialActionMovie(frintezza, 1000, 90, 30, 0, 5000, 0);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(3), 3000);
					break;
				
				case 3:
					// show movie
					showSocialActionMovie(frintezza, 140, 90, 0, 6000, 6000, 2);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(5), 5990);
					
					break;
				
				case 5:
					// show movie
					showSocialActionMovie(frintezza, 240, 90, 3, 22000, 6000, 3);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(6), 5800);
					
					break;
				
				case 6:
					// show movie
					showSocialActionMovie(frintezza, 240, 90, 3, 300, 6000, 0);
					frintezza.broadcastPacket(new MagicSkillUse(frintezza, frintezza, 5006, 1,
							_intervalOfFrintezzaSongs, 0), 360000/* 600 */);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(7), 5800);
					
					weakScarlet = (L2GrandBossInstance)scarletSpawnWeak.doSpawn();
					weakScarlet.setIsImmobilized(true);
					weakScarlet.setIsInSocialAction(true);
					
					portrait1 = (L2GrandBossInstance)portraitSpawn1.doSpawn();
					portrait1.setIsImmobilized(true);
					
					portrait2 = (L2GrandBossInstance)portraitSpawn2.doSpawn();
					portrait2.setIsImmobilized(true);
					
					portrait3 = (L2GrandBossInstance)portraitSpawn3.doSpawn();
					portrait3.setIsImmobilized(true);
					
					portrait4 = (L2GrandBossInstance)portraitSpawn4.doSpawn();
					portrait4.setIsImmobilized(true);
					
					demon1 = (L2MonsterInstance)demonSpawn1.doSpawn();
					demon1.setIsImmobilized(true);
					
					demon2 = (L2MonsterInstance)demonSpawn2.doSpawn();
					demon2.setIsImmobilized(true);
					
					demon3 = (L2MonsterInstance)demonSpawn3.doSpawn();
					demon3.setIsImmobilized(true);
					
					demon4 = (L2MonsterInstance)demonSpawn4.doSpawn();
					demon4.setIsImmobilized(true);
					
					Earthquake eq = new Earthquake(weakScarlet.getX(), weakScarlet.getY(), weakScarlet.getZ(), 50, 6);
					
					for (L2Player pc : getPlayersInside())
						pc.broadcastPacket(eq);
					
					break;
				
				case 7:
					
					showSocialActionMovie(demon1, 140, 0, 3, 22000, 3000, 1);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(8), 2800);
					break;
				
				case 8:
					
					showSocialActionMovie(demon2, 140, 0, 3, 22000, 3000, 1);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(9), 2800);
					break;
				
				case 9:
					
					showSocialActionMovie(demon3, 140, 180, 3, 22000, 3000, 1);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(10), 2800);
					break;
				
				case 10:
					
					showSocialActionMovie(demon4, 140, 180, 3, 22000, 3000, 1);
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(17), 2800);
					
					weakScarlet.broadcastPacket(new SocialAction(weakScarlet.getObjectId(), 2));
					break;
				
				case 17:
					
					// show movie
					for (L2Player pc : getPlayersInside())
					{
						pc.setTarget(null);
						
						pc.disableAllSkills();
						
						if (pc.getPlanDistanceSq(weakScarlet) <= 6502500)
						{
							pc.enterMovieMode();
							pc.specialCamera(weakScarlet, 1700, 180, -90, 0, 4000);
						}
						else
						{
							pc.leaveMovieMode();
						}
					}
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(18), 3800);
					
					break;
				
				case 18:
					// show movie
					showSocialActionMovie(weakScarlet, 1500, 270, -70, 6000, 7000, 2);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(19), 6900);
					
					break;
				
				case 19:
					// show movie
					showSocialActionMovie(weakScarlet, 1500, 0, -60, 0, 5000, 2);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(20), 4900);
					
					break;
				
				case 20:
					// show movie
					showSocialActionMovie(weakScarlet, 1220, 90, -70, 300, 2000, 0);
					
					// set next task.
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(21), 1900);
					
					break;
				
				case 21:
					
					weakScarlet.abortCast();
					weakScarlet.enableAllSkills();
					
					ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(weakScarlet), 16);
					
					// L2CharPosition pos = new L2CharPosition(174234, -88015, -5116, 48028);
					// _moveAtRandomTask = ThreadPoolManager.getInstance().scheduleGeneral(new MoveAtRandom(weakScarlet, pos) , 32);
					weakScarlet.teleToLocation(174234, -88015, -5116, false);
					
					showSocialActionMovie(weakScarlet, 1000, 270, 19, 300, 3000, 2);
					
					ThreadPoolManager.getInstance().scheduleGeneral(new ScarletWeakSpawn(32), 3100);
					
					teleportToFinish();
					
					// set delete task.
					_activityTimeEndTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ActivityTimeEnd(), _activityTimeOfBoss);
					
					break;
				
				case 32:
					
					// reset camera.
					for (L2Player pc : getPlayersInside())
					{
						pc.leaveMovieMode();
						pc.enableAllSkills();
					}
					
					frintezza.abortCast();
					frintezza.enableAllSkills();
					
					L2Skill skill = SkillTable.getInstance().getInfo(1086, 1);
					
					demon1.setIsImmobilized(false);
					ThreadPoolManager.getInstance().scheduleGeneral(
							new doSkill(demon1, skill, _intervalOfFrintezzaSongs, 1000), 4000);
					
					demon2.setIsImmobilized(false);
					ThreadPoolManager.getInstance().scheduleGeneral(
							new doSkill(demon2, skill, _intervalOfFrintezzaSongs, 1000), 4100);
					
					demon3.setIsImmobilized(false);
					ThreadPoolManager.getInstance().scheduleGeneral(
							new doSkill(demon3, skill, _intervalOfFrintezzaSongs, 1000), 4200);
					
					demon4.setIsImmobilized(false);
					ThreadPoolManager.getInstance().scheduleGeneral(
							new doSkill(demon4, skill, _intervalOfFrintezzaSongs, 1000), 4300);
					
					ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(frintezza), 16);
					
					// Start random attacks on players for Frintezza
					ThreadPoolManager.getInstance().scheduleGeneral(new ReTarget(frintezza), _intervalOfRetarget);
					
					// Start random attacks on players for Scarlet
					ThreadPoolManager.getInstance()
							.scheduleGeneral(new ReTarget(weakScarlet), _intervalOfRetarget + 16);
					
					ThreadPoolManager.getInstance().scheduleGeneral(new Music(), Rnd.get(_intervalOfFrintezzaSongs));
					
					startAttackListeners();
					
					break;
			
			}
		}
		
	}
	
	/** ******************************************************************************************** */
	
	/***********************************************************************************************************************************************************
	 * ****** M M PPPPPPP TTTTTTTTT Y Y /* MM MM P P T Y Y /* M M M M P P T Y Y /******* M M M M P P T Y /* M M M PPPPPPP T Y /* M M P T Y /* M M P T Y
	 * /******** M M P T Y /*********************************************************************************************** /** Frintezza's songs, needed
	 * special implementation since core doesn't support 3 stage skills * /
	 **********************************************************************************************************************************************************/
	
	/**
	 * @author Darki699 Three stages of casts: 1. Song, cast on Frintezza, for the music to play (skill 5007, levels 1-5) Each level is a different tune =) 2.
	 *         Visual Effect, cast on targets, to show the effect (skill 5008, levels 1-5) Each level has a different animation effect and different targets and
	 *         purpose 3. Actual skill, which is different since NCSoft has a different skill system. I used other skill Ids to implement these effects: song
	 *         effects: 1. skill 1217 - Greater Heal (5007,1 -> 5008,1 -> 1217,33) 2. skill 1204 - Wind Walk (5007,2 -> 5008,2 -> 1204,2) 3. skill 1086 - Haste
	 *         Buff (5007,3 -> 5008,3 -> 1086,2) 4. skill 406 - Angelic Icon (5007,4 -> 5008,4 -> 406,3 only gainHp*0.2 func added) 5. no skill only immobilize
	 *         (5007,5 -> 5008,5 -> dance+stun animation + Immobilizes)
	 */
	
	private class Music implements Runnable
	{
		@Override
		public void run()
		{
			if (frintezza == null)
				return;
			
			int song = getSong();
			if (song < 1)
				song = 1;
			else if (song > 5)
				song = 5;
			
			frintezza.broadcastPacket(
					new MagicSkillUse(frintezza, frintezza, 5007, song, _intervalOfFrintezzaSongs, 0), 10000);
			
			int currentHp = (int)frintezza.getStatus().getCurrentHp();
			
			// Launch the song's effects (they start about 10 seconds after he starts to play)
			ThreadPoolManager.getInstance().scheduleGeneral(
					new SongEffectLaunched(getSongTargets(song), song, currentHp, 10000), 10000);
			
			// Schedule a new song to be played in 30-40 seconds...
			ThreadPoolManager.getInstance().scheduleGeneral(new Music(), _intervalOfFrintezzaSongs + Rnd.get(10000));
		}
		
		/**
		 * Depending on the song, returns the song's targets (either mobs or players)
		 * 
		 * @param songId
		 *            (1-5 songs)
		 * @return L2Object[] targets
		 */
		private L2Object[] getSongTargets(int songId)
		{
			
			List<L2Object> targets = new ArrayList<L2Object>();
			
			if (songId < 4) // Target is the minions
			{
				
				if (weakScarlet != null && !weakScarlet.isDead())
					targets.add(weakScarlet);
				
				if (strongScarlet != null && !strongScarlet.isDead())
					targets.add(strongScarlet);
				
				if (portrait1 != null && !portrait1.isDead())
					targets.add(portrait1);
				
				if (portrait2 != null && !portrait2.isDead())
					targets.add(portrait2);
				
				if (portrait3 != null && !portrait3.isDead())
					targets.add(portrait3);
				
				if (portrait4 != null && !portrait4.isDead())
					targets.add(portrait4);
				
				if (demon1 != null && !demon1.isDead())
					targets.add(demon1);
				
				if (demon2 != null && !demon2.isDead())
					targets.add(demon2);
				
				if (demon3 != null && !demon3.isDead())
					targets.add(demon3);
				
				if (demon4 != null && !demon4.isDead())
					targets.add(demon4);
				
				targets.add(frintezza);
			}
			
			else
			// Target is the players
			{
				
				for (L2Player pc : getPlayersInside())
				{
					if (!pc.isDead())
						targets.add(pc);
				}
			}
			
			return targets.toArray(new L2Object[targets.size()]);
		}
		
		/**
		 * returns the chosen symphony for Frintezza to play If the minions are injured he has 40% to play a healing song If they are all dead, he will only
		 * play harmful player symphonies
		 * 
		 * @return
		 */
		
		private int getSong()
		{
			if (minionsNeedHeal())
				return 1;
			else if (minionsAreDead())
				return Rnd.get(4, 6);
			return Rnd.get(2, 6);
		}
		
		/**
		 * Checks if the main minions are dead (not including demons, only Scarlet and Portraits)
		 * 
		 * @return boolean true if all main minions are dead
		 */
		
		private boolean minionsAreDead()
		{
			if (weakScarlet != null && !weakScarlet.isDead())
				return false;
			
			else if (strongScarlet != null && !strongScarlet.isDead())
				return false;
			
			else if (portrait1 != null && !portrait1.isDead())
				return false;
			
			else if (portrait2 != null && !portrait2.isDead())
				return false;
			
			else if (portrait3 != null && !portrait3.isDead())
				return false;
			
			else if (portrait4 != null && !portrait4.isDead())
				return false;
			
			return true;
		}
		
		/**
		 * Checks if Frintezza's minions need heal (only major minions are checked) Return a "need heal" = true only 40% of the time
		 * 
		 * @return boolean value true if need to play a healing minion song
		 */
		private boolean minionsNeedHeal()
		{
			boolean returnValue = false;
			
			if (weakScarlet != null && !weakScarlet.isAlikeDead()
					&& weakScarlet.getStatus().getCurrentHp() < weakScarlet.getMaxHp() * 2 / 3)
				
				returnValue = true;
			
			else if (strongScarlet != null && !strongScarlet.isAlikeDead()
					&& strongScarlet.getStatus().getCurrentHp() < strongScarlet.getMaxHp() * 2 / 3)
				
				returnValue = true;
			
			else if ((portrait1 != null && !portrait1.isAlikeDead() && portrait1.getStatus().getCurrentHp() < portrait1
					.getMaxHp() / 3)
					|| (portrait2 != null && !portrait2.isAlikeDead() && portrait2.getStatus().getCurrentHp() < portrait2
							.getMaxHp() / 3)
					|| (portrait3 != null && !portrait3.isAlikeDead() && portrait3.getStatus().getCurrentHp() < portrait3
							.getMaxHp() / 3)
					|| (portrait4 != null && !portrait4.isAlikeDead() && portrait4.getStatus().getCurrentHp() < portrait4
							.getMaxHp() / 3))
				
				returnValue = true;
			
			if (returnValue && Rnd.get(100) > 40) // 40% to heal minions when needed.
				return false;
			
			return returnValue;
		}
	}
	
	/**
	 * The song was played, this class checks it's affects (if any)
	 * 
	 * @author Darki699
	 */
	private class SongEffectLaunched implements Runnable
	{
		private final L2Object[] _targets;
		
		private final int _song, _previousHp, _currentTime;
		
		/**
		 * Constructor
		 * 
		 * @param targets -
		 *            song's targets L2Object[]
		 * @param song -
		 *            song id 1-5
		 * @param previousHp -
		 *            Frintezza's HP when he started to play
		 * @param currentTimeOfSong -
		 *            skills during music play are consecutive, repeating
		 */
		public SongEffectLaunched(L2Object[] targets, int song, int previousHp, int currentTimeOfSong)
		{
			_targets = targets;
			_song = song;
			_previousHp = previousHp;
			_currentTime = currentTimeOfSong;
		}
		
		@Override
		public void run()
		{
			if (frintezza == null)
				return;
			
			// If the song time is over stop this loop
			else if (_currentTime > _intervalOfFrintezzaSongs)
				return;
			
			// Skills are consecutive, so call them again
			SongEffectLaunched songLaunched =
					new SongEffectLaunched(_targets, _song, (int)frintezza.getStatus().getCurrentHp(), _currentTime
							+ _intervalOfFrintezzaSongs / 10);
			ThreadPoolManager.getInstance().scheduleGeneral(songLaunched, _intervalOfFrintezzaSongs / 10);
			
			// If Frintezza got injured harder than his regen rate, do not launch the song.
			if (_previousHp > frintezza.getStatus().getCurrentHp())
			{
				L2Object frintezzaTarget = frintezza.getTarget();
				
				if (frintezzaTarget != null && frintezzaTarget instanceof L2Creature)
					callMinionsToAssist(((L2Creature)frintezzaTarget), 200);
				
				return;
			}
			
			for (L2Object target : _targets)
			{
				if (target == null || !(target instanceof L2Creature))
					continue;
				
				L2Creature cha = (L2Creature)target;
				
				if (cha.isDead() || cha.isInvul())
					continue;
				
				// show the magic effect on the target - visual effect
				cha.broadcastPacket(new MagicSkillUse(frintezza, cha, 5008, _song, 2000, 0), 10000);
				
				// calculate the song's damage
				calculateSongEffects(cha);
			}
		}
		
		/**
		 * Calculates the music damage according to the current song played
		 * 
		 * @param target -
		 *            L2Creature affected by the music
		 */
		private void calculateSongEffects(L2Creature target)
		{
			if (target == null)
				return;
			try
			{
				L2Skill skill;
				
				switch (_song)
				{
					case 1: // Consecutive Heal : Greater Heal - on the monsters
						skill = SkillTable.getInstance().getInfo(1217, 33);
						frintezza.callSkill(skill, target);
						break;
					
					case 2: // Consecutive Dash : Wind Walk - monsters run faster
						skill = SkillTable.getInstance().getInfo(1204, 2);
						frintezza.callSkill(skill, target);
						break;
					
					case 3: // Affecting Atk Spd : Haste Buff - monsters attack faster
						skill = SkillTable.getInstance().getInfo(1086, 2);
						frintezza.callSkill(skill, target);
						break;
					
					case 4: // Offensive Skill: Decreases the effect of HP reg. on the players
						
						if (Rnd.get(100) < 80) // 80% success not considering m.def + p.def ???
						{
							// Launch the skill on the target to decrease it's HP
							decreaseEffectOfHpReg(target);
						}
						break;
					
					case 5: // Offensive Skill: Immoblizes - dance+stun. Player is immoblized
						skill = SkillTable.getInstance().getInfo(5008, 5);
						if (Rnd.get(100) < 80) // 80% success not considering m.def + p.def ???
						{
							new startStunDanceEffect(target, skill).run();
						}
						break;
				}
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
		}
	}
	
	/**
	 * Decreases the HP Regeneration of the <b>target</b>
	 * 
	 * @param target
	 *            L2Creature who's HP Regeneration is decreased.
	 */
	private void decreaseEffectOfHpReg(L2Creature target)
	{
		L2Skill skill = SkillTable.getInstance().getInfo(5008, 4);
		
		frintezza.callSkill(skill, target);
		
		// send target the message, the skill was launched
		if (target instanceof L2Player)
			target.getActingPlayer().sendPacket(
					new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(skill.getId()));
		
		// Add stat funcs to the target
		target.addStatFunc(getDecreaseRegHpFunc());
		
		// Set exit timer for these stats
		ThreadPoolManager.getInstance().scheduleGeneral(new exitDecreaseRegHp(target), getDebuffPeriod(skill, target));
	}
	
	/**
	 * Returns the duration of the <b>skill</b> on the <b>target</b>.
	 * 
	 * @param skill
	 *            L2Skill to calculate it's duration
	 * @param target
	 *            L2Creature to calculate the duration on it
	 * @return int value of skill duration before exit
	 */
	private int getDebuffPeriod(L2Skill skill, L2Creature target)
	{
		// This is usually returned, unless _debuffPeriod needs initialization
		if (_debuffPeriod != 0)
			return _debuffPeriod;
		
		// Initialize _debuffPeriod
		if (skill == null || target == null)
		{
			_debuffPeriod = 15000;
			
			return _debuffPeriod;
		}
		
		//		for (L2Effect effect : skill.getEffects(frintezza, target, true))
		//		{
		//			if (effect == null)
		//				continue;
		//
		//			_debuffPeriod = effect.getPeriod() * 1000;
		//		}
		
		if (_debuffPeriod == 0)
			_debuffPeriod = 15000;
		
		// return _debuffPeriod which is now initialized
		return _debuffPeriod;
	}
	
	/**
	 * 20% hp regeneration
	 * 
	 * @return <b>Func</b> the functions needed to decrease the Hp Regeneration from the targets
	 */
	private Func getDecreaseRegHpFunc()
	{
		if (_DecreaseRegHp == null)
			_DecreaseRegHp = new FuncMul(Stats.REGENERATE_HP_RATE, 0x30, FUNC_OWNER, 0.2, null);
		
		return _DecreaseRegHp;
	}
	
	private final FuncOwner FUNC_OWNER = new FuncOwner() {
		@Override
		public String getFuncOwnerName()
		{
			return null;
		}
		
		@Override
		public L2Skill getFuncOwnerSkill()
		{
			return null;
		}
	};
	
	/**
	 * Class made to exit the debuff effect of the DecreaseRegHp symphony
	 * 
	 * @author Darki699
	 */
	private class exitDecreaseRegHp implements Runnable
	{
		private final L2Creature _char;
		
		public exitDecreaseRegHp(L2Creature character)
		{
			_char = character;
		}
		
		@Override
		public void run()
		{
			if (_char != null)
				_char.removeStatsOwner(FUNC_OWNER);
		}
	}
	
	/**
	 * Further implementation into the core is needed. But this will do for now ;] Class needed to implement the start Frintezza dance+stun effect on a target.
	 * 
	 * @author Darki699
	 */
	
	private class startStunDanceEffect implements Runnable
	{
		private final L2Creature _effected;
		
		private final L2Skill _skill;
		
		public startStunDanceEffect(L2Creature target, L2Skill skill)
		{
			_effected = target;
			_skill = skill;
		}
		
		@Override
		public void run()
		{
			try
			{
				if (_effected == null)
					return;
				
				//stun dance can be cast on L2Player only
				if (!(_effected instanceof L2Player))
					return;
				
				if (_effected.getFirstEffect(_skill) != null)
					return;
				
				L2Player effected = (L2Player)_effected;
				if (effected.isInvul() || effected.getAppearance().isInvisible())
					return;
				
				// stop all actions
				setIdle(_effected);
				
				_effected.setTarget(null);
				
				// start the animation
				_effected.startAbnormalEffect(AbnormalEffect.DANCE_STUNNED);
				
				// add the effect icon
				_effected.callSkill(_skill, _effected);
				
				// send target the message
				if (_effected instanceof L2Player)
					_effected.getActingPlayer().sendPacket(
							new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT).addSkillName(_skill.getId()));
				
				// set the cancel task for this effect
				ThreadPoolManager.getInstance().scheduleGeneral(new exitStunDanceEffect(_effected),
						getDebuffPeriod(_skill, _effected));
			}
			catch (RuntimeException e)
			{
				if (_effected != null && !_effected.isAlikeDead())
				{
					_effected.enableAllSkills();
					_effected.setIsImmobilized(false);
				}
				
				_log.warn("", e);
			}
		}
	}
	
	/**
	 * Ends the dance+stun effect on the target
	 * 
	 * @author Darki699
	 */
	private class exitStunDanceEffect implements Runnable
	{
		private final L2Creature _effected;
		
		public exitStunDanceEffect(L2Creature target)
		{
			_effected = target;
		}
		
		@Override
		public void run()
		{
			if (_effected == null)
				return;
			
			_effected.enableAllSkills();
			_effected.setIsImmobilized(false);
			
			_effected.stopAbnormalEffect(AbnormalEffect.DANCE_STUNNED);
		}
	}
	
	/** ************************** End of Frintezza's Musical effects ******************************** */
	
	/***********************************************************************************************************************************************************
	 * ****** M M PPPPPPP TTTTTTTTT Y Y /* MM MM P P T Y Y /* M M M M P P T Y Y /******* M M M M P P T Y /* M M M PPPPPPP T Y /* M M P T Y /* M M P T Y
	 * /******** M M P T Y /************************ Minion Control Attack + Respawn + Polymorph
	 **********************************************************************************************************************************************************/
	
	/**
	 * Initializes the Attack Listeners for <b>all</b> monsters in this zone. Sends a thread loop (tasks canceled ofcourse) with the mob to be listened to, and
	 * the amount of hate it sends to all other mobs regarding it's attacker.
	 */
	
	private void startAttackListeners()
	{
		// Set listeners for the Demons.
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(demon1, 1), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(demon2, 1), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(demon3, 1), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(demon4, 1), Rnd.get(2000));
		
		// Set listeners for the Portraits.
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(portrait1, 50), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(portrait2, 50), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(portrait3, 50), Rnd.get(2000));
		
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(portrait4, 50), Rnd.get(2000));
		
		// Set a listener for Frintezza.
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(frintezza, 200), Rnd.get(2000));
		
		// Set a listener for the weaker version of Scarlet Van Halisha.
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(weakScarlet, 100), Rnd.get(2000));
		
		// Note that Scarlet's strong version isn't spawned yet. An attack listener for it will
		// be added when and if he's spawned.
	}
	
	/**
	 * Class is recalled at an interval for a monster's life time. Once the monster is <b>deleted</b> (null), this class is not called anymore If the monster
	 * is <b>dead</b>, this class is still called at the interval, but it does nothing until next respawn. If the monster is <b>alive</b> and is being
	 * attacked, it "tells" the other monsters that it's attacked.
	 * 
	 * @author Darki699
	 */
	private class attackerListener implements Runnable
	{
		private final L2Creature _mob;
		
		private final int _aggroDamage;
		
		public attackerListener(L2Creature controller, int hate)
		{
			_mob = controller;
			_aggroDamage = hate;
		}
		
		@Override
		public void run()
		{
			// If the monster is deleted, return.
			if (_mob == null)
				return;
			
			// Set next listener.
			ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(_mob, _aggroDamage),
					_callForHelpInterval + Rnd.get(500));
			
			// If the mob is dead, we do nothing until next respawn
			if (_mob.isDead())
			{
				try
				{
					// if this is a demon, decay it until next respawn.
					if (!(_mob instanceof L2GrandBossInstance))
						_mob.decayMe();
					else
						// if this is a boss, we need to do a few checks:
						// portraits - unspawn demons, set both to null
						// Scarlet, Frintezza - check if all bosses are dead.
						bossDeadCheck((L2GrandBossInstance)_mob);
				}
				catch (RuntimeException e)
				{
					_log.warn("", e);
				}
				return;
			}
			// Added a check, since mobs sometimes move toward their targets (teleport)
			// He shouldn't move from his place
			else if (_mob == frintezza)
			{
				if (_mob.getX() != frintezzaSpawn.getLocx() || _mob.getY() != frintezzaSpawn.getLocy())
				{
					boolean[] targeted = getTargeted(_mob);
					L2Object target = _mob.getTarget();
					
					_mob.getPosition().setXYZ(frintezzaSpawn.getLocx(), frintezzaSpawn.getLocy(),
							frintezzaSpawn.getLocz());
					
					_mob.decayMe();
					_mob.spawnMe();
					
					_mob.setTarget(target);
					setTargeted(_mob, targeted);
				}
			}
			
			// Tell the other mobs "I'm attacked"
			L2Object target = _mob.getTarget();
			
			if (target == null || (target instanceof L2Creature && ((L2Creature)target).isDead()))
				return;
			
			/*
			 * if (target instanceof L2Player && (((L2Player)target).isInvul() || ((L2Player)target).getAppearance().getInvisible())) {
			 * _mob.abortAttack(); _mob.abortCast(); _mob.setTarget(null); _mob.getKnownList().getKnownPlayers().remove((L2Player)target); return; }
			 */
			if (target instanceof L2Creature)
				callMinionsToAssist((L2Creature)target, _aggroDamage);
			
			// Now set the mob's Target to the most hated:
			if (_mob instanceof L2Attackable)
			{
				L2Creature mostHated = ((L2Attackable)_mob).getMostHated();
				
				if (mostHated != null)
				{
					_mob.setTarget(mostHated);
				}
			}
		}
	}
	
	/**
	 * If the dead boss is a Portrait, we delete it from the world, and it's demon as well If the dead boss is Scarlet or Frintezza, we do a bossesAreDead()
	 * check to see if both Frintezza and Scarlet are dead.
	 * 
	 * @param mob -
	 *            L2BossInstance that is (or is set as) dead.
	 */
	
	public void bossDeadCheck(L2Npc mob)
	{
		if (mob == null)
			return;
		
		// !!! Frintezza or Scarlet should NEVER be called from setUnspawn() to this function !!!
		// It will cause a deadlock.
		if (mob == weakScarlet || mob == strongScarlet)
		{
			if (bossesAreDead())
				doUnspawn();
			return;
		}
		
		L2MonsterInstance demon = null;
		
		if (mob == portrait1)
		{
			portrait1 = null;
			demon = demon1;
			demon1 = null;
		}
		else if (mob == portrait2)
		{
			portrait2 = null;
			demon = demon2;
			demon2 = null;
		}
		else if (mob == portrait3)
		{
			portrait3 = null;
			demon = demon3;
			demon3 = null;
		}
		else if (mob == portrait4)
		{
			portrait4 = null;
			demon = demon4;
			demon4 = null;
		}
		
		// Try to delete the portrait.
		try
		{
			mob.decayMe();
			mob.deleteMe();
			mob = null;
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		
		// Try to delete the portraits demon.
		if (demon != null)
		{
			try
			{
				demon.decayMe();
				demon.deleteMe();
				demon = null;
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
		}
	}
	
	/**
	 * controls the assistance for all 3 bosses: 1. if Frintezza needs help, all (including Scarlet van Halisha) help him 2. if Scarlet needs help, all
	 * (including Frintezza) come to his help 3. if Strong Scarlet is already spawned, then he teleports to help Frintezza
	 * 
	 * @param attacker - The player that attacked the boss
	 * @param hate - Damage hate to add to the attacker 1. Frintezza adds 200 hate 2. Weak Scarlet adds 100 hate 3. Stronger Scarlet adds 125 hate 4.
	 *            Strongest Scarlet adds 150 hate 5. Portraits adds 50 hate 6. Demons adds 1 hate
	 */
	public void callMinionsToAssist(L2Creature attacker, int hate)
	{
		if (attacker == null)
			return;
		
		if (demon1 != null && !demon1.isDead())
			demon1.addDamage(attacker, hate);
		else
			checkRespawnTime(demon1);
		
		if (demon2 != null && !demon2.isDead())
			demon2.addDamage(attacker, hate);
		else
			checkRespawnTime(demon2);
		
		if (demon3 != null && !demon3.isDead())
			demon3.addDamage(attacker, hate);
		else
			checkRespawnTime(demon3);
		
		if (demon4 != null && !demon4.isDead())
			demon4.addDamage(attacker, hate);
		else
			checkRespawnTime(demon4);
		
		if (weakScarlet != null && !weakScarlet.isDead())
		{
			weakScarletHpListener();
			if (weakScarlet != null && !weakScarlet.isDead())
				weakScarlet.addDamage(attacker, hate);
		}
		else
			bossesAreDead();
		
		if (strongScarlet != null && !strongScarlet.isDead())
			strongScarlet.addDamage(attacker, hate);
		else
			bossesAreDead();
	}
	
	/**
	 * Checks on a killed demon to set it's respawn time (only done if the <b>Portrait</b> of <b>Demon</b> was not killed)
	 * 
	 * @param mob -
	 *            L2MonsterInstance mob of the Demon that was killed
	 * @return int value of the demon that should be respawned. -1 is returned if the demon should not respawn now.
	 */
	public int checkRespawnTime(L2MonsterInstance mob)
	{
		if (mob == null)
			return -1;
		
		if (mob == demon1)
		{
			if (portrait1 == null || portrait1.isDead())
				return -1;
			else if (_respawningDemon1)
				return 1;
			
			_respawningDemon1 = true;
		}
		
		else if (mob == demon2)
		{
			if (portrait2 == null || portrait2.isDead())
				return -1;
			else if (_respawningDemon2)
				return 2;
			
			_respawningDemon2 = true;
		}
		
		else if (mob == demon3)
		{
			if (portrait3 == null || portrait3.isDead())
				return -1;
			else if (_respawningDemon3)
				return 3;
			
			_respawningDemon3 = true;
		}
		
		else if (mob == demon4)
		{
			if (portrait4 == null || portrait4.isDead())
				return -1;
			else if (_respawningDemon4)
				return 4;
			
			_respawningDemon4 = true;
		}
		
		ThreadPoolManager.getInstance().scheduleGeneral(new respawnDemon(mob), _intervalOfDemons);
		return -1;
	}
	
	/**
	 * Class respawns a demon if it's portrait is not dead.
	 * 
	 * @author Darki699
	 */
	
	private class respawnDemon implements Runnable
	{
		private final L2MonsterInstance _mob;
		
		public respawnDemon(L2MonsterInstance mob)
		{
			_mob = mob;
		}
		
		@Override
		public void run()
		{
			int demonId = checkRespawnTime(_mob);
			
			switch (demonId)
			{
				case -1: // If it's portrait is dead do not respawn the demon.
					try
					{
						_mob.decayMe();
						_mob.deleteMe();
					}
					catch (RuntimeException e)
					{
						_log.warn("", e);
					}
					
					return;
					
				case 1: // Demon #1 is respawned
					_mob.getPosition().setXYZ(demonSpawn1.getLocx(), demonSpawn1.getLocy(), demonSpawn1.getLocz());
					_respawningDemon1 = false;
					break;
				
				case 2: // Demon #2 is respawned
					_mob.getPosition().setXYZ(demonSpawn2.getLocx(), demonSpawn2.getLocy(), demonSpawn2.getLocz());
					_respawningDemon2 = false;
					break;
				
				case 3: // Demon #3 is respawned
					_mob.getPosition().setXYZ(demonSpawn3.getLocx(), demonSpawn3.getLocy(), demonSpawn3.getLocz());
					_respawningDemon3 = false;
					break;
				
				case 4: // Demon #4 is respawned
					_mob.getPosition().setXYZ(demonSpawn4.getLocx(), demonSpawn4.getLocy(), demonSpawn4.getLocz());
					_respawningDemon4 = false;
					break;
			}
			
			_mob.doRevive();
			_mob.getStatus().setCurrentHp(_mob.getMaxHp());
			_mob.getStatus().setCurrentMp(_mob.getMaxMp());
			_mob.spawnMe();
			
			L2Creature target = getRandomPlayer();
			_mob.setTarget(target);
			
			L2CharPosition pos = new L2CharPosition(target.getX(), target.getY(), target.getZ(), 0);
			new MoveToPos(_mob, pos);
		}
	}
	
	/**
	 * Listens to the weaker Scarlet Van Halisha HP status. If it's weakened enough, we spawn the Stronger version of Scarlet and delete the weaker one.
	 */
	
	public void weakScarletHpListener()
	{
		if (weakScarlet == null || weakScarlet.isDead())
			return;
		
		double curHp = weakScarlet.getStatus().getCurrentHp(), maxHp = weakScarlet.getMaxHp();
		
		if (_scarletIsWeakest)
		{
			// Morph Scarlet Van Halisha into a Stronger one ;]
			if (curHp < maxHp * 2 / 3)
			{
				doSecondMorph();
			}
		}
		else
		{
			if (curHp < maxHp * 1 / 3)
			{
				// Do 3rd Morph, Scarlet Van Halisha now changes templates
				doThirdMorph();
			}
		}
	}
	
	/**
	 * Does the 3rd and last polymorph for Scarlet Van Halisha. Now he looks entirely different... (he is different)
	 */
	private void doThirdMorph()
	{
		setIdle(weakScarlet);
		
		weakScarlet.setIsInSocialAction(true);
		
		// animation
		weakScarlet.getPoly().setPolyInfo("npc", "29047");
		MagicSkillUse msk = new MagicSkillUse(weakScarlet, 1008, 1, 4000, 0);
		weakScarlet.broadcastPacket(msk);
		
		// set Strong Scarlet's position and heading
		scarletSpawnStrong.setLocx(weakScarlet.getX());
		scarletSpawnStrong.setLocy(weakScarlet.getY());
		scarletSpawnStrong.setLocz(weakScarlet.getZ());
		scarletSpawnStrong.setHeading(weakScarlet.getHeading());
		scarletSpawnStrong.setRespawnDelay(_intervalOfBoss);
		scarletSpawnStrong.stopRespawn();
		
		// spawn Strong Scarlet and set his HP to the weaker version:
		strongScarlet = (L2GrandBossInstance)scarletSpawnStrong.doSpawn();
		double newHp = weakScarlet.getStatus().getCurrentHp();
		strongScarlet.getStatus().setCurrentHp(newHp);
		
		// Immobilize Strong Scarlet
		setIdle(strongScarlet);
		strongScarlet.setIsInSocialAction(true);
		
		// do a social action "hello" ;]
		strongScarlet.broadcastPacket(new SocialAction(strongScarlet.getObjectId(), 2));
		
		// update his target
		strongScarlet.setTarget(weakScarlet.getTarget());
		
		// restore the original weapon into the template
		weakScarlet.getTemplate().setRhand(8204);
		
		// get weakScarlet's list of attackers (or players that targeted it).
		boolean[] targeted = getTargeted(weakScarlet);
		
		// set the list of players to target strongScarlet
		setTargeted(strongScarlet, targeted);
		
		// delete the weakScarlet from the world
		scarletSpawnWeak.stopRespawn();
		scarletSpawnWeak.decreaseCount(weakScarlet);
		
		weakScarlet.getPoly().setPolyInfo(null, "1");
		weakScarlet.decayMe();
		weakScarlet.deleteMe();
		weakScarlet = null;
		
		// add Attack Listener
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(strongScarlet, 150), Rnd.get(4000) + 1000);
		
		// add retarget Listener
		ThreadPoolManager.getInstance().scheduleGeneral(new ReTarget(strongScarlet), _intervalOfRetarget);
		
		// mobilize Strong Scarlet
		ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(strongScarlet), 4000);
		
		// set teleport speed
		L2Skill skill = SkillTable.getInstance().getInfo(1086, 1);
		ThreadPoolManager.getInstance().scheduleGeneral(new doSkill(strongScarlet, skill, _intervalOfRetarget, 300),
				4016);
	}
	
	/**
	 * Receives a target and a list of players in _playersInLair that should target this target
	 * 
	 * @param target
	 *            L2Creature
	 * @param targeted
	 *            boolean[]
	 * @return void
	 */
	
	private void setTargeted(L2Creature target, boolean[] targeted)
	{
		int count = 0;
		
		// Server->Client packet StatusUpdate of the L2Npc to the L2Player to update its HP bar
		StatusUpdate su = new StatusUpdate(target.getObjectId());
		su.addAttribute(StatusUpdate.CUR_HP, (int)target.getStatus().getCurrentHp());
		su.addAttribute(StatusUpdate.MAX_HP, target.getMaxHp());
		
		// set the target again on the players that targeted this _caster
		for (L2Player pc : getPlayersInside())
		{
			if (pc != null && targeted[count])
			{
				pc.setTarget(target);
				
				// Send a Server->Client packet StatusUpdate of the L2Npc to the L2Player to update its HP bar
				pc.sendPacket(su);
			}
			count++;
		}
	}
	
	/**
	 * Receives a target and returns the _playersInLair that target this target
	 * 
	 * @param target
	 *            L2Object
	 * @return boolean[] targeted players (true = target , false = not target)
	 */
	
	private boolean[] getTargeted(L2Object target)
	{
		boolean[] targeted = new boolean[getPlayersInside().size()];
		int count = 0;
		
		// get the players that targeted this _caster
		for (L2Player pc : getPlayersInside())
		{
			targeted[count] = !(pc == null || pc.getTarget() != target);
			count++;
		}
		return targeted;
	}
	
	/**
	 * Sets a L2Creature to idle state. Disables all skills, aborts attack and cast, immoblizies
	 * 
	 * @param target
	 *            L2Creature
	 */
	public void setIdle(L2Creature target)
	{
		target.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		target.abortAttack();
		target.abortCast();
		target.setIsImmobilized(true);
		target.disableAllSkills();
	}
	
	/**
	 * Does the 2nd Morph for Scarlet Van Halisha. Now he's bigger and he teleports to his targets
	 */
	
	private void doSecondMorph()
	{
		_scarletIsWeakest = false;
		
		weakScarlet.getTemplate().setRhand(7903);
		
		L2Spawn scarletSpawnTemp =
				createNewSpawn(29046, weakScarlet.getX(), weakScarlet.getY(), weakScarlet.getZ(),
						weakScarlet.getHeading(), _intervalOfBoss);
		
		L2GrandBossInstance tempScarlet = (L2GrandBossInstance)scarletSpawnTemp.doSpawn();
		tempScarlet.getStatus().setCurrentHp(weakScarlet.getStatus().getCurrentHp());
		tempScarlet.setTarget(weakScarlet.getTarget());
		boolean[] targeted = getTargeted(weakScarlet);
		
		weakScarlet.decayMe();
		weakScarlet.deleteMe();
		weakScarlet = tempScarlet;
		
		setTargeted(weakScarlet, targeted);
		
		setIdle(weakScarlet);
		
		weakScarlet.setIsInSocialAction(true);
		weakScarlet.broadcastPacket(new SocialAction(weakScarlet.getObjectId(), 2));
		
		// showSocialActionMovie (weakScarlet , 140, 90, 3, 0, 1000, 1);
		// showSocialActionMovie (weakScarlet , 120, 80, 3, 0, 1000, 2);
		
		// add a NEW Attack Listener
		ThreadPoolManager.getInstance().scheduleGeneral(new attackerListener(weakScarlet, 125), Rnd.get(4000) + 1000);
		
		// add a NEW retarget Listener
		ThreadPoolManager.getInstance().scheduleGeneral(new ReTarget(weakScarlet), _intervalOfRetarget * 2 / 3);
		
		// start teleporting fast
		L2Skill skill = SkillTable.getInstance().getInfo(1086, 1);
		ThreadPoolManager.getInstance().scheduleGeneral(new doSkill(weakScarlet, skill, _intervalOfRetarget, 200), 50);
		
		skill = SkillTable.getInstance().getInfo(1068, 3);
		weakScarlet.callSkill(skill, weakScarlet);
		
		ThreadPoolManager.getInstance().scheduleGeneral(new SetMobilised(weakScarlet), 1100);
		
		// reset camera.
		for (L2Player pc : getPlayersInside())
		{
			pc.leaveMovieMode();
			pc.enableAllSkills();
		}
	}
	
	/**
	 * Starts the skill effects for Scarlet Van Halisha. He moves like the wind ;] Continuous skills cast at _intervalOfRetarget
	 * 
	 * @author Darki699
	 */
	
	private class doSkill implements Runnable
	{
		private final L2Creature _caster;
		private final L2Skill _skill;
		private final int _interval, _range;
		
		/**
		 * Shows skill animation effect and teleports to the target if it's out of range
		 * 
		 * @param caster -
		 *            the monster
		 * @param skill -
		 *            the skill to animate
		 * @param interval -
		 *            the time between leaps
		 * @param range -
		 *            the range minimum to teleport
		 */
		
		public doSkill(L2Creature caster, L2Skill skill, int interval, int range)
		{
			_caster = caster;
			_skill = skill;
			_interval = interval;
			_range = range;
		}
		
		@Override
		public void run()
		{
			if (_caster == null || _caster.isDead())
				return;
			
			try
			{
				_caster.enableAllSkills();
				
				L2Object tempTarget = _caster.getTarget();
				
				if (tempTarget == null || !(tempTarget instanceof L2Creature))
					tempTarget = _caster;
				
				int x = tempTarget.getX() + Rnd.get(_range) - _range / 2, y =
						tempTarget.getY() + Rnd.get(_range) - _range / 2, z = tempTarget.getZ();
				
				if (!_caster.isInsideRadius(x, y, _range, false) && tempTarget instanceof L2Player
						&& checkIfInZone((L2Player)tempTarget))
				{
					
					// Returns a list of the players that targeted the _caster
					boolean[] targeted = getTargeted(_caster);
					
					_caster.broadcastPacket(new MagicSkillUse(_caster, ((L2Creature)tempTarget), _skill.getId(),
							_skill.getLevel(), 0, 0), 10000);
					_caster.decayMe();
					_caster.getPosition().setXYZ(x, y, z);
					_caster.spawnMe(x, y, z);
					_caster.setTarget(tempTarget);
					
					// retarget all the players that targeted this _caster
					setTargeted(_caster, targeted);
					
				}
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
			
			ThreadPoolManager.getInstance().scheduleGeneral(new doSkill(_caster, _skill, _interval, _range),
					_interval + Rnd.get(500));
		}
	}
	
	/** * Re-Target Class to update a monster's known list and to re-target it again at an interval ** */
	
	private class ReTarget implements Runnable
	{
		private final L2Npc _mob;
		
		public ReTarget(L2Npc mob)
		{
			_mob = mob;
		}
		
		@Override
		public void run()
		{
			if (_mob == null || _mob.isDead() || getPlayersInside().isEmpty())
			{
				if (bossesAreDead() || getPlayersInside().isEmpty())
				{
					// Unspawn in 20 seconds...
					doUnspawn();
				}
				return;
			}
			
			_mob.setTarget(getRandomPlayer());
			
			if (_mob.getTarget() == null)
				ThreadPoolManager.getInstance().scheduleGeneral(new ReTarget(_mob), 1000);
			else
				ThreadPoolManager.getInstance().scheduleGeneral(new ReTarget(_mob), _intervalOfRetarget);
		}
	}
	
	/** *********************************** End of re-target class *************************************** */
	
	/**
	 * starts the unspawn in 20 seconds.
	 */
	private void doUnspawn()
	{
		ThreadPoolManager.getInstance().scheduleGeneral(new Unspawn(), 20000);
	}
	
	/**
	 * Unspawns class calls setUnspawn() function to end this battle scene
	 * 
	 * @author Darki699
	 */
	private class Unspawn implements Runnable
	{
		@Override
		public void run()
		{
			setUnspawn();
		}
	}
	
	/**
	 * Checks if Frintezza and Scarlet Van Halisha are <b>both</b> dead
	 * 
	 * @return boolean true if <b>all</b> bosses are dead.
	 */
	
	private boolean bossesAreDead()
	{
		if (weakScarlet == null && strongScarlet == null)
			return true;
		
		int deadCount = 0;
		
		if ((weakScarlet != null && weakScarlet.isDead()) || weakScarlet == null)
			deadCount++;
		
		if ((strongScarlet != null && strongScarlet.isDead()) || strongScarlet == null)
			deadCount++;
		
		return deadCount == 2;
	}
	
	/**
	 * Class ends the activity of the Bosses after a interval of time Exits the battle field in any way ...
	 * 
	 * @author Darki699
	 */
	private class ActivityTimeEnd implements Runnable
	{
		@Override
		public void run()
		{
			setUnspawn();
		}
	}
	
	/**
	 * Clean Frintezza's lair.
	 */
	@Override
	public void setUnspawn()
	{
		NpcTable.getInstance().getTemplate(29046).setRhand(8204);
		
		// eliminate players.
		banishForeigners();
		
		// delete monsters.
		bossDeadCheck(portrait1); // Deletes portrait and demon
		
		bossDeadCheck(portrait2); // Deletes portrait and demon
		
		bossDeadCheck(portrait3); // Deletes portrait and demon
		
		bossDeadCheck(portrait4); // Deletes portrait and demon
		
		try
		{
			if (frintezza != null)
			{
				frintezza.decayMe();
				frintezza.deleteMe();
			}
			
			if (strongScarlet != null)
			{
				strongScarlet.decayMe();
				strongScarlet.deleteMe();
			}
			
			if (weakScarlet != null)
			{
				weakScarlet.decayMe();
				weakScarlet.deleteMe();
			}
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		
		frintezza = strongScarlet = weakScarlet = null;
		_state.setState(GrandBossState.StateEnum.DEAD);
		
		// delete spawns
		if (frintezzaSpawn != null)
			frintezzaSpawn.stopRespawn();
		if (scarletSpawnWeak != null)
			scarletSpawnWeak.stopRespawn();
		if (scarletSpawnStrong != null)
			scarletSpawnStrong.stopRespawn();
		if (portraitSpawn1 != null)
			portraitSpawn1.stopRespawn();
		if (portraitSpawn2 != null)
			portraitSpawn2.stopRespawn();
		if (portraitSpawn3 != null)
			portraitSpawn3.stopRespawn();
		if (portraitSpawn4 != null)
			portraitSpawn4.stopRespawn();
		if (demonSpawn1 != null)
			demonSpawn1.stopRespawn();
		if (demonSpawn2 != null)
			demonSpawn2.stopRespawn();
		if (demonSpawn3 != null)
			demonSpawn3.stopRespawn();
		if (demonSpawn4 != null)
			demonSpawn4.stopRespawn();
		
		frintezzaSpawn = scarletSpawnWeak = scarletSpawnStrong = null;
		
		portraitSpawn1 = portraitSpawn2 = portraitSpawn3 = portraitSpawn4 = null;
		
		demonSpawn1 = demonSpawn2 = demonSpawn3 = demonSpawn4 = null;
		
		// not executed tasks are canceled.
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
		
		// interval begin.... Count until Frintezza is ready to respawn again.
		setIntervalEndTask();
	}
	
	/**
	 * Creates a thread to initialize Frintezza again... until this loops ends, no one can enter the lair.
	 */
	public void setIntervalEndTask()
	{
		if (!_state.getState().equals(GrandBossState.StateEnum.INTERVAL))
		{
			_state.setRespawnDate(_intervalOfBoss);
			_state.setState(GrandBossState.StateEnum.INTERVAL);
			_state.update();
		}
		_intervalEndTask = ThreadPoolManager.getInstance().scheduleGeneral(new IntervalEnd(), _state.getInterval());
	}
	
	/**
	 * Calls for a re-initialization when time comes, only then can players enter the lair.
	 * 
	 * @author Darki699
	 */
	
	private class IntervalEnd implements Runnable
	{
		@Override
		public void run()
		{
			_state.setState(GrandBossState.StateEnum.NOTSPAWN);
			_state.update();
		}
	}
	
	/**
	 * Class used to make the monster/boss mobile again.
	 * 
	 * @author Darki699
	 */
	private class SetMobilised implements Runnable
	{
		private final L2Npc _boss;
		
		public SetMobilised(L2Npc boss)
		{
			_boss = boss;
		}
		
		@Override
		public void run()
		{
			_boss.setIsImmobilized(false);
			if (_boss instanceof L2GrandBossInstance)
				((L2GrandBossInstance)_boss).setIsInSocialAction(false);
		}
	}
	
	/**
	 * Moves an L2Npc to a new Position.
	 * 
	 * @author Darki699
	 */
	private class MoveToPos implements Runnable
	{
		private final L2Npc _npc;
		private final L2CharPosition _pos;
		
		public MoveToPos(L2Npc npc, L2CharPosition pos)
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
}
