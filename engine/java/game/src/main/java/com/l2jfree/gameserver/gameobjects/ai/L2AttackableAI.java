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
package com.l2jfree.gameserver.gameobjects.ai;

import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_ACTIVE;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_IDLE;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_INTERACT;

import java.util.Set;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2ChestInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FestivalMonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FriendlyMobInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2GuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MinionInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * This class manages AI of L2Attackable.<BR><BR>
 * 
 */
public class L2AttackableAI extends L2CreatureAI implements Runnable
{
	private static final class AttackableAiTaskManager extends AbstractIterativePeriodicTaskManager<L2AttackableAI>
	{
		private static final AttackableAiTaskManager _instance = new AttackableAiTaskManager();
		
		private static AttackableAiTaskManager getInstance()
		{
			return _instance;
		}
		
		private AttackableAiTaskManager()
		{
			super(1000);
		}
		
		@Override
		protected void callTask(L2AttackableAI task)
		{
			task.run();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "run()";
		}
	}
	
	private static final int RANDOM_WALK_RATE = 30; // confirmed
	// private static final int MAX_DRIFT_RANGE = 300;
	private static final int MAX_ATTACK_TIMEOUT = 300; // int ticks, i.e. 30 seconds
	
	/** The delay after which the attacked is stopped */
	private int _attackTimeout;
	
	/** The L2Attackable aggro counter */
	private int _globalAggro;
	
	/** The flag used to indicate that a thinking action is in progress */
	private volatile boolean _thinking; // to prevent recursive thinking
	
	/** For attack AI, analysis of mob and its targets */
	private final SelfAnalysis _selfAnalysis = new SelfAnalysis();
	private final TargetAnalysis _mostHatedAnalysis = new TargetAnalysis();
	private final TargetAnalysis _secondMostHatedAnalysis = new TargetAnalysis();
	
	/**
	 * Constructor of L2AttackableAI.<BR><BR>
	 * 
	 * @param accessor The AI accessor of the L2Creature
	 * 
	 */
	public L2AttackableAI(L2Creature.AIAccessor accessor)
	{
		super(accessor);
		
		_selfAnalysis.init(_actor);
		_attackTimeout = Integer.MAX_VALUE;
		_globalAggro = -10; // 10 seconds timeout of ATTACK after respawn
	}
	
	@Override
	public void run()
	{
		// Launch actions corresponding to the Event Think
		notifyEvent(CtrlEvent.EVT_THINK);
	}
	
	/**
	 * Return True if the target is autoattackable (depends on the actor type).<BR><BR>
	 * 
	 * <B><U> Actor is a L2GuardInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The L2Player target has karma (=PK)</li>
	 * <li>The L2MonsterInstance target is aggressive</li><BR><BR>
	 * 
	 * <B><U> Actor is a L2SiegeGuardInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>A siege is in progress</li>
	 * <li>The L2Player target isn't a Defender</li><BR><BR>
	 * 
	 * <B><U> Actor is a L2FriendlyMobInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk, a Door or another L2Npc</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The L2Player target has karma (=PK)</li><BR><BR>
	 * 
	 * <B><U> Actor is a L2MonsterInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk, a Door or another L2Npc</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The actor is Aggressive</li><BR><BR>
	 * 
	 * @param target The targeted L2Object
	 * 
	 */
	private boolean autoAttackCondition(L2Creature target)
	{
		if (target == null || !(_actor instanceof L2Attackable))
			return false;
		L2Attackable me = (L2Attackable)_actor;
		
		// Check if the target isn't invulnerable
		if (target.isInvul())
		{
			// However EffectInvincible requires to check GMs specially
			if (target instanceof L2Playable && target.getActingPlayer().isGM())
				return false;
		}
		
		// Check if the target isn't a Folk or a Door
		if (target instanceof L2NpcInstance || target instanceof L2DoorInstance)
			return false;
		
		// Check if the target isn't dead, is in the Aggro range and is at the same height
		if (target.isAlikeDead() || !me.isInsideRadius(target, me.getAggroRange(), false, false)
				|| Math.abs(_actor.getZ() - target.getZ()) > 300)
			return false;
		
		if (_selfAnalysis.cannotMoveOnLand && !target.isInsideZone(L2Zone.FLAG_WATER))
			return false;
		
		// Check if the target is a L2Playable
		if (target instanceof L2Playable)
		{
			// Check if the AI isn't a Raid Boss and the target isn't in silent move mode
			if (!(me instanceof L2Boss) && ((L2Playable)target).isSilentMoving())
				return false;
		}
		
		// Check if the target is a L2Player
		if (target instanceof L2Player)
		{
			L2Player player = (L2Player)target;
			// Don't take the aggro if the GM has the access level below or equal to GM_DONT_TAKE_AGGRO
			if (player.isGM() && player.getAccessLevel() <= Config.GM_DONT_TAKE_AGGRO)
				return false;
			
			// TODO: Ideally, autoattack condition should be called from the AI script.  In that case,
			// it should only implement the basic behaviors while the script will add more specific
			// behaviors (like varka/ketra alliance, etc).  Once implemented, remove specialized stuff
			// from this location.  (Fulminus)
			
			// Check if player is an ally (comparing mem addr)
			if (me.getFactionId() == "varka" && player.isAlliedWithVarka())
				return false;
			else if (me.getFactionId() == "ketra" && player.isAlliedWithKetra())
				return false;
			
			//if player is disguised beleth faction ignores him
			if (me.getFactionId() == "beleth" && player.isTransformed() && player.getTransformationId() == 101)
				return false;
			
			//event playere are also ignored
			if (player.isInFunEvent())
				return false;
			
			// check if the target is within the grace period for JUST getting up from fake death
			if (player.isRecentFakeDeath())
				return false;
		}
		// Check if the target is a L2Summon
		if (target instanceof L2Summon)
		{
			L2Player owner = ((L2Summon)target).getOwner();
			if (owner != null)
			{
				// Don't take the aggro if the GM has the access level below or equal to GM_DONT_TAKE_AGGRO
				if (owner.isGM() && (owner.isInvul() || owner.getAccessLevel() <= Config.GM_DONT_TAKE_AGGRO))
					return false;
				// Check if player is an ally (comparing mem addr)
				if (me.getFactionId() == "varka" && owner.isAlliedWithVarka())
					return false;
				if (me.getFactionId() == "ketra" && owner.isAlliedWithKetra())
					return false;
			}
		}
		// Check if the actor is a L2GuardInstance
		if (_actor instanceof L2GuardInstance)
		{
			// Check if the L2Player target has karma (=PK)
			if (target instanceof L2Player && ((L2Player)target).getKarma() > 0)
				// Los Check
				return GeoData.getInstance().canSeeTarget(me, target);
			
			//if (target instanceof L2Summon)
			//  return ((L2Summon)target).getKarma() > 0;
			
			// Check if the L2MonsterInstance target is aggressive
			if (target instanceof L2MonsterInstance)
				return (((L2MonsterInstance)target).isAggressive() && GeoData.getInstance().canSeeTarget(me, target));
			
			return false;
		}
		else if (_actor instanceof L2FriendlyMobInstance)
		{ // the actor is a L2FriendlyMobInstance
		
			// Check if the actor is a L2FriendlyMobInstance
			
			// Check if the target isn't another L2Npc
			if (target instanceof L2Npc)
				return false;
			
			// Check if the L2Player target has karma (=PK)
			if (target instanceof L2Player && ((L2Player)target).getKarma() > 0)
				// Los Check
				return GeoData.getInstance().canSeeTarget(me, target);
			return false;
		}
		else
		{ //The actor is a L2MonsterInstance
		
			// Check if the target isn't another L2Npc
			if (target instanceof L2Npc)
				return false;
			
			// depending on config, do not allow mobs to attack _new_ players in peacezones,
			// unless they are already following those players from outside the peacezone.
			if (!Config.ALT_MOB_AGGRO_IN_PEACEZONE && target.isInsideZone(L2Zone.FLAG_PEACE))
				return false;
			
			if (me.isChampion() && Config.CHAMPION_PASSIVE)
				return false;
			
			// Check if the actor is Aggressive
			return (me.isAggressive() && GeoData.getInstance().canSeeTarget(me, target));
		}
	}
	
	public void startAITask()
	{
		AttackableAiTaskManager.getInstance().startTask(this);
		
		if (_actor instanceof L2GuardInstance)
			((L2GuardInstance)_actor).startReturnTask();
	}
	
	@Override
	public void stopAITask()
	{
		if (_actor instanceof L2GuardInstance)
			((L2GuardInstance)_actor).stopReturnTask();
		
		AttackableAiTaskManager.getInstance().stopTask(this);
		_accessor.detachAI();
	}
	
	@Override
	protected void onEvtDead()
	{
		stopAITask();
		super.onEvtDead();
	}
	
	/**
	 * Set the Intention of this L2CreatureAI and create an  AI Task executed every 1s (call onEvtThink method) for this L2Attackable.<BR><BR>
	 * 
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If actor _knowPlayer isn't EMPTY, AI_INTENTION_IDLE will be change in AI_INTENTION_ACTIVE</B></FONT><BR><BR>
	 * 
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention
	 * @param arg1 The second parameter of the Intention
	 * 
	 */
	@Override
	synchronized void changeIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		if (intention == AI_INTENTION_IDLE || intention == AI_INTENTION_ACTIVE)
		{
			// Check if actor is not dead
			if (!_actor.isAlikeDead())
			{
				L2Attackable npc = (L2Attackable)_actor;
				
				// If its _knownPlayer isn't empty set the Intention to AI_INTENTION_ACTIVE
				if (!npc.getKnownList().getKnownPlayers().isEmpty())
					intention = AI_INTENTION_ACTIVE;
				else
				{
					if (npc.getSpawn() != null)
					{
						final int range = Config.MAX_DRIFT_RANGE;
						if (!npc.isInsideRadius(npc.getSpawn().getLocx(), npc.getSpawn().getLocy(), npc.getSpawn()
								.getLocz(), range + range, true, false))
							intention = AI_INTENTION_ACTIVE;
					}
				}
			}
			
			if (intention == AI_INTENTION_IDLE)
			{
				// Set the Intention of this L2AttackableAI to AI_INTENTION_IDLE
				super.changeIntention(AI_INTENTION_IDLE, null, null);
				
				// Stop AI task and detach AI from NPC
				stopAITask();
				return;
			}
		}
		
		// Set the Intention of this L2AttackableAI to intention
		super.changeIntention(intention, arg0, arg1);
		
		// If not idle - create an AI task (schedule onEvtThink repeatedly)
		startAITask();
	}
	
	/**
	 * Manage the Attack Intention : Stop current Attack (if necessary), Calculate attack timeout, Start a new Attack and Launch Think Event.<BR><BR>
	 *
	 * @param target The L2Creature to attack
	 *
	 */
	@Override
	protected void onIntentionAttack(L2Creature target)
	{
		// Calculate the attack timeout
		_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
		
		// self and buffs
		if (_selfAnalysis.lastBuffTick + 100 < GameTimeManager.getGameTicks())
		{
			for (L2Skill sk : _selfAnalysis.buffSkills)
			{
				if (_actor.getFirstEffect(sk.getId()) == null)
				{
					if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
						continue;
					if (_actor.isSkillDisabled(sk.getId()))
						continue;
					// no clan buffs here?
					if (sk.getTargetType() == L2Skill.SkillTargetType.TARGET_CLAN)
						continue;
					L2Object OldTarget = _actor.getTarget();
					_actor.setTarget(_actor);
					clientStopMoving(null);
					_accessor.doCast(sk);
					// forcing long reuse delay so if cast get interrupted or there would be several buffs, doesn't cast again
					_selfAnalysis.lastBuffTick = GameTimeManager.getGameTicks();
					_actor.setTarget(OldTarget);
				}
			}
		}
		// Manage the Attack Intention : Stop current Attack (if necessary), Start a new Attack and Launch Think Event
		super.onIntentionAttack(target);
	}
	
	/**
	 * Manage AI standard thinks of a L2Attackable (called by onEvtThink).<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Update every 1s the _globalAggro counter to come close to 0</li>
	 * <li>If the actor is Aggressive and can attack, add all autoAttackable L2Creature in its Aggro Range to its _aggroList, chose a target and order to attack it</li>
	 * <li>If the actor is a L2GuardInstance that can't attack, order to it to return to its home location</li>
	 * <li>If the actor is a L2MonsterInstance that can't attack, order to it to random walk (1/100)</li><BR><BR>
	 * 
	 */
	protected void thinkActive()
	{
		L2Attackable npc = (L2Attackable)_actor;
		
		// Update every 1s the _globalAggro counter to come close to 0
		if (_globalAggro != 0)
		{
			if (_globalAggro < 0)
				_globalAggro++;
			else
				_globalAggro--;
		}
		/*
				switch (npc.getFleeingStatus())
				{
				// 10 million
				case L2Attackable.FLEEING_STARTED:
				// 1 million
				case L2Attackable.FLEEING_DONE_RETURNING:
					return;
				}
		*/
		// Add all autoAttackable L2Creature in L2Attackable Aggro Range to its _aggroList with 0 damage and 1 hate
		// A L2Attackable isn't aggressive during 10s after its spawn because _globalAggro is set to -10
		if (_globalAggro >= 0)
		{
			// Get all visible objects inside its Aggro Range
			//L2Object[] objects = L2World.getInstance().getVisibleObjects(_actor, ((L2Npc)_actor).getAggroRange());
			// Go through visible objects
			for (L2Object obj : npc.getKnownList().getKnownObjects().values())
			{
				if (!(obj instanceof L2Creature))
					continue;
				L2Creature target = (L2Creature)obj;
				
				/*
				 * Check to see if this is a festival mob spawn.
				 * If it is, then check to see if the aggro trigger
				 * is a festival participant...if so, move to attack it.
				 */
				if ((_actor instanceof L2FestivalMonsterInstance) && obj instanceof L2Player)
				{
					L2Player targetPlayer = (L2Player)obj;
					
					if (!(targetPlayer.isFestivalParticipant()))
						continue;
				}
				
				/*
				 * Temporarily adding this commented code as a concept to be used eventually.
				 * However, the way it is written below will NOT work correctly.  The NPC
				 * should only notify Aggro Range Enter when someone enters the range from outside.
				 * Instead, the below code will keep notifying even while someone remains within
				 * the range.  Perhaps we need a short knownlist of range = aggroRange for just
				 * people who are actively within the npc's aggro range?...(Fulminus)
				// notify AI that a playable instance came within aggro range
				if ((obj instanceof L2Player) || (obj instanceof L2Summon))
				{
				    if ( !((L2Creature)obj).isAlikeDead()
				            && !npc.isInsideRadius(obj, npc.getAggroRange(), true, false) )
				    {
				        L2Player targetPlayer = (obj instanceof L2Player)? (L2Player) obj: ((L2Summon) obj).getOwner();
				        if (npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_AGGRO_RANGE_ENTER) !=null)
				            for (Quest quest: npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_AGGRO_RANGE_ENTER))
				                quest.notifyAggroRangeEnter(npc, targetPlayer, (obj instanceof L2Summon));
				    }
				}
				*/
				// TODO: The AI Script ought to handle aggro behaviors in onSee.  Once implemented, aggro behaviors ought
				// to be removed from here.  (Fulminus)
				// For each L2Creature check if the target is autoattackable
				if (autoAttackCondition(target)) // check aggression
				{
					// Get the hate level of the L2Attackable against this L2Creature target contained in _aggroList
					int hating = npc.getHating(target);
					
					// Add the attacker to the L2Attackable _aggroList with 0 damage and 1 hate
					if (hating == 0)
						npc.addDamageHate(target, 0, 0);
				}
			}
			
			// Chose a target from its aggroList
			L2Creature hated;
			if (_actor.isConfused())
				hated = getAttackTarget(); // effect handles selection
			else
				hated = npc.getMostHated();
			
			// Order to the L2Attackable to attack the target
			if (hated != null)
			{
				// Get the hate level of the L2Attackable against this L2Creature target contained in _aggroList
				int aggro = npc.getHating(hated);
				
				if (aggro + _globalAggro > 0)
				{
					// Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player
					if (!_actor.isRunning())
						_actor.setRunning();
					
					// Set the AI Intention to AI_INTENTION_ATTACK
					setIntention(CtrlIntention.AI_INTENTION_ATTACK, hated);
					// [L2J_JP ADD]
					// following boss
					L2MinionInstance minion;
					L2MonsterInstance boss;
					Set<L2MinionInstance> minions;
					if (_actor instanceof L2MonsterInstance)
					{
						boss = (L2MonsterInstance)_actor;
						if (boss.hasMinions())
						{
							minions = boss.getSpawnedMinions();
							for (L2MinionInstance m : minions)
							{
								if (!m.isRunning())
									m.setRunning();
								m.getAI().startFollow(_actor);
							}
						}
					}
					else if (_actor instanceof L2MinionInstance)
					{
						minion = (L2MinionInstance)_actor;
						boss = minion.getLeader();
						if (!boss.isRunning())
							boss.setRunning();
						boss.getAI().startFollow(_actor);
						minions = boss.getSpawnedMinions();
						for (L2MinionInstance m : minions)
						{
							if (!(m.getObjectId() == _actor.getObjectId()))
							{
								if (!m.isRunning())
									m.setRunning();
								m.getAI().startFollow(_actor);
							}
						}
						
					}
				}
				
				return;
			}
			
		}
		
		// Check if the actor is a L2GuardInstance
		if (_actor instanceof L2GuardInstance)
		{
			// Order to the L2GuardInstance to return to its home location because there's no target to attack
			((L2GuardInstance)_actor).returnHome();
		}
		
		// If this is a festival monster, then it remains in the same location.
		if (_actor instanceof L2FestivalMonsterInstance)
			return;
		
		// Check if the mob should not return to spawn point
		if (!npc.canReturnToSpawnPoint())
			return;
		
		// Minions following leader
		if (_actor instanceof L2MinionInstance && ((L2MinionInstance)_actor).getLeader() != null)
		{
			int offset;
			
			if (_actor.isRaid())
				offset = 500; // for Raid minions - need correction
			else
				offset = 200; // for normal minions - need correction :)
				
			if (((L2MinionInstance)_actor).getLeader().isRunning())
				_actor.setRunning();
			else
				_actor.setWalking();
			
			if (_actor.getPlanDistanceSq(((L2MinionInstance)_actor).getLeader()) > offset * offset)
			{
				int x1, y1, z1;
				x1 = ((L2MinionInstance)_actor).getLeader().getX() + Rnd.nextInt((offset - 30) * 2) - (offset - 30);
				y1 = ((L2MinionInstance)_actor).getLeader().getY() + Rnd.nextInt((offset - 30) * 2) - (offset - 30);
				z1 = ((L2MinionInstance)_actor).getLeader().getZ();
				// Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet MoveToLocation (broadcast)
				moveTo(x1, y1, z1);
			}
			else if (Rnd.nextInt(RANDOM_WALK_RATE) == 0)
			{
				// self and clan buffs
				for (L2Skill sk : _selfAnalysis.buffSkills)
				{
					if (_actor.getFirstEffect(sk.getId()) == null)
					{
						// if clan buffs, don't buff every time
						if (sk.getTargetType() != L2Skill.SkillTargetType.TARGET_SELF && Rnd.nextInt(2) != 0)
							continue;
						if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
							continue;
						if (_actor.isSkillDisabled(sk.getId()))
							continue;
						L2Object OldTarget = _actor.getTarget();
						_actor.setTarget(_actor);
						clientStopMoving(null);
						_accessor.doCast(sk);
						_actor.setTarget(OldTarget);
						return;
					}
				}
			}
		}
		// Order to the L2MonsterInstance to random walk (1/100)
		else if (npc.getSpawn() != null && Rnd.nextInt(RANDOM_WALK_RATE) == 0 && !_actor.isNoRndWalk())
		{
			// self and clan buffs
			for (L2Skill sk : _selfAnalysis.buffSkills)
			{
				if (_actor.getFirstEffect(sk.getId()) == null)
				{
					// if clan buffs, don't buff every time
					if (sk.getTargetType() != L2Skill.SkillTargetType.TARGET_SELF && Rnd.nextInt(2) != 0)
						continue;
					if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
						continue;
					if (_actor.isSkillDisabled(sk.getId()))
						continue;
					L2Object OldTarget = _actor.getTarget();
					_actor.setTarget(_actor);
					clientStopMoving(null);
					_accessor.doCast(sk);
					_actor.setTarget(OldTarget);
					return;
				}
			}
			
			if (_actor instanceof L2Boss || _actor instanceof L2MinionInstance || _actor instanceof L2ChestInstance
					|| _actor instanceof L2GuardInstance)
				return;
			
			final int range = Config.MAX_DRIFT_RANGE;
			
			int x1;
			int y1;
			int z1;
			if (npc.getFleeingStatus() == L2Attackable.FLEEING_NOT_STARTED || npc.getMoveAroundPos() == null)
			{
				x1 = npc.getSpawn().getLocx();
				y1 = npc.getSpawn().getLocy();
				z1 = npc.getSpawn().getLocz();
			}
			else
			{
				x1 = npc.getMoveAroundPos().x;
				y1 = npc.getMoveAroundPos().y;
				z1 = npc.getMoveAroundPos().z;
			}
			
			if (!_actor.isInsideRadius(x1, y1, z1, range + range, true, false))
			{
				npc.setisReturningToSpawnPoint(true);
			}
			else
			{
				x1 += Rnd.nextInt(range * 2) - range;
				y1 += Rnd.nextInt(range * 2) - range;
				z1 = npc.getZ();
			}
			
			//_log.config("Curent pos ("+getX()+", "+getY()+"), moving to ("+x1+", "+y1+").");
			// Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet MoveToLocation (broadcast)
			moveTo(x1, y1, z1);
			// [L2J_JP ADD]
			// following boss
			if (_actor instanceof L2MonsterInstance)
			{
				L2MonsterInstance boss = (L2MonsterInstance)_actor;
				boss.callMinions();
			}
		}
	}
	
	/**
	 * Manage AI attack thinks of a L2Attackable (called by onEvtThink).<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Update the attack timeout if actor is running</li>
	 * <li>If target is dead or timeout is expired, stop this attack and set the Intention to AI_INTENTION_ACTIVE</li>
	 * <li>Call all L2Object of its Faction inside the Faction Range</li>
	 * <li>Chose a target and order to attack it with magic skill or physical attack</li><BR><BR>
	 */
	protected void thinkAttack()
	{
		if (_attackTimeout < GameTimeManager.getGameTicks())
		{
			// Check if the actor is running
			if (_actor.isRunning())
			{
				// Set the actor movement type to walk and send Server->Client packet ChangeMoveType to all others L2Player
				_actor.setWalking();
				
				// Calculate a new attack timeout
				_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
			}
		}
		
		L2Creature originalAttackTarget = getAttackTarget();
		// Check if target is dead or if timeout is expired to stop this attack
		if (originalAttackTarget == null || originalAttackTarget.isAlikeDead()
				|| _attackTimeout < GameTimeManager.getGameTicks())
		{
			// Stop hating this target after the attack timeout or if target is dead
			if (originalAttackTarget != null)
				((L2Attackable)_actor).stopHating(originalAttackTarget);
			
			// Set the AI Intention to AI_INTENTION_ACTIVE
			setIntention(AI_INTENTION_ACTIVE);
			
			_actor.setWalking();
			return;
		}
		
		// Handle all L2Object of its Faction inside the Faction Range
		if (((L2Npc)_actor).getFactionId() != null)
		{
			String faction_id = ((L2Npc)_actor).getFactionId();
			
			// Go through all L2Object that belong to its faction
			for (L2Object obj : _actor.getKnownList().getKnownObjects().values())
			{
				if (obj instanceof L2Npc)
				{
					L2Npc npc = (L2Npc)obj;
					
					//Handle SevenSigns mob Factions
					String npcfaction = npc.getFactionId();
					boolean sevenSignFaction = false;
					
					// TODO: Unhardcode this by AI scripts (DrHouse)
					//Catacomb mobs should assist lilim and nephilim other than dungeon
					if ((faction_id == "c_dungeon_clan")
							&& ((npcfaction == "c_dungeon_lilim") || npcfaction == "c_dungeon_nephi"))
						sevenSignFaction = true;
					//Lilim mobs should assist other Lilim and catacomb mobs
					else if ((faction_id == "c_dungeon_lilim") && (npcfaction == "c_dungeon_clan"))
						sevenSignFaction = true;
					//Nephilim mobs should assist other Nephilim and catacomb mobs
					else if ((faction_id == "c_dungeon_nephi") && (npcfaction == "c_dungeon_clan"))
						sevenSignFaction = true;
					
					if (faction_id != npc.getFactionId() && !sevenSignFaction)
						continue;
					
					// Check if the L2Object is inside the Faction Range of the actor
					if (_actor.isInsideRadius(npc, npc.getFactionRange() + npc.getTemplate().getCollisionRadius(),
							true, false) && npc.getAI() != null)
					{
						if (Math.abs(originalAttackTarget.getZ() - npc.getZ()) < 600
								&& _actor.getAttackByList().contains(originalAttackTarget)
								&& (npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE || npc.getAI()
										.getIntention() == CtrlIntention.AI_INTENTION_ACTIVE)
								&& GeoData.getInstance().canSeeTarget(_actor, npc))
						{
							if ((originalAttackTarget instanceof L2Player)
									|| (originalAttackTarget instanceof L2Summon))
							{
								L2Player player =
										(originalAttackTarget instanceof L2Player)
												? (L2Player)originalAttackTarget : ((L2Summon)originalAttackTarget)
														.getOwner();
								Quest[] quests = npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_FACTION_CALL);
								if (quests != null)
								{
									for (Quest quest : quests)
										quest.notifyFactionCall(npc, (L2Npc)_actor, player,
												(originalAttackTarget instanceof L2Summon));
								}
							}
						}
						// heal or resurrect friends
						if (_selfAnalysis.hasHealOrResurrect && !_actor.isAttackingDisabled()
								&& npc.getStatus().getCurrentHp() < npc.getMaxHp() * 0.6
								&& _actor.getStatus().getCurrentHp() > _actor.getMaxHp() / 2
								&& _actor.getStatus().getCurrentMp() > _actor.getMaxMp() / 2
						
						)
						{
							if (npc.isDead() && _actor instanceof L2MinionInstance)
							{
								if (((L2MinionInstance)_actor).getLeader() == npc)
								{
									for (L2Skill sk : _selfAnalysis.resurrectSkills)
									{
										if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
											continue;
										if (_actor.isSkillDisabled(sk.getId()))
											continue;
										if (!Util.checkIfInRange(sk.getCastRange(), _actor, npc, true))
											continue;
										
										if (10 >= Rnd.get(100)) // chance
											continue;
										if (!GeoData.getInstance().canSeeTarget(_actor, npc))
											break;
										
										L2Object OldTarget = _actor.getTarget();
										_actor.setTarget(npc);
										// would this ever be fast enough for the decay not to run?
										// giving some extra seconds
										DecayTaskManager.getInstance().cancelDecayTask(npc);
										DecayTaskManager.getInstance().addDecayTask(npc);
										clientStopMoving(null);
										_accessor.doCast(sk);
										_actor.setTarget(OldTarget);
										return;
									}
								}
							}
							else if (npc.isInCombat())
							{
								for (L2Skill sk : _selfAnalysis.healSkills)
								{
									if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
										continue;
									if (_actor.isSkillDisabled(sk.getId()))
										continue;
									if (!Util.checkIfInRange(sk.getCastRange(), _actor, npc, true))
										continue;
									
									int chance = 4;
									if (_actor instanceof L2MinionInstance)
									{
										// minions support boss
										if (((L2MinionInstance)_actor).getLeader() == npc)
											chance = 6;
										else
											chance = 3;
									}
									if (npc instanceof L2Boss)
										chance = 6;
									if (chance >= Rnd.get(100)) // chance
										continue;
									if (!GeoData.getInstance().canSeeTarget(_actor, npc))
										break;
									
									L2Object OldTarget = _actor.getTarget();
									_actor.setTarget(npc);
									clientStopMoving(null);
									_accessor.doCast(sk);
									_actor.setTarget(OldTarget);
									return;
								}
							}
						}
					}
				}
			}
		}
		
		if (_actor.isAttackingDisabled())
			return;
		
		switch (((L2Attackable)_actor).getFleeingStatus())
		{
		// 10 million
			case L2Attackable.FLEEING_STARTED:
				// 1 million
			case L2Attackable.FLEEING_DONE_RETURNING:
				return;
		}
		
		// Get 2 most hated chars
		L2Creature[] hated = ((L2Attackable)_actor).get2MostHated();
		if (_actor.isConfused())
		{
			if (hated != null)
				hated[0] = originalAttackTarget; // effect handles selection
			else
				hated = new L2Creature[] { originalAttackTarget, null };
		}
		
		if (hated == null || hated[0] == null)
		{
			setIntention(AI_INTENTION_ACTIVE);
			return;
		}
		if (hated[0] != originalAttackTarget)
		{
			setAttackTarget(hated[0]);
		}
		_mostHatedAnalysis.update(_actor, hated[0]);
		_secondMostHatedAnalysis.update(_actor, hated[1]);
		
		// Get all information needed to choose between physical or magical attack
		_actor.setTarget(_mostHatedAnalysis.character);
		double dist2 =
				_actor.getPlanDistanceSq(_mostHatedAnalysis.character.getX(), _mostHatedAnalysis.character.getY());
		int combinedCollision =
				_actor.getTemplate().getCollisionRadius()
						+ _mostHatedAnalysis.character.getTemplate().getCollisionRadius();
		int range = _actor.getPhysicalAttackRange() + combinedCollision;
		
		// Reconsider target next round if _actor hasn't got hits in for last 14 seconds
		if (!_actor.isMuted() && _attackTimeout - 160 < GameTimeManager.getGameTicks()
				&& _secondMostHatedAnalysis.character != null)
		{
			if (Util.checkIfInRange(900, _actor, hated[1], true))
			{
				// take off 2* the amount the aggro is larger than second most
				((L2Attackable)_actor).reduceHate(hated[0],
						2 * (((L2Attackable)_actor).getHating(hated[0]) - ((L2Attackable)_actor).getHating(hated[1])));
				// Calculate a new attack timeout
				_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
			}
		}
		// Reconsider target during next round if actor is rooted and cannot reach mostHated but can
		// reach secondMostHated
		if (_actor.isRooted() && _secondMostHatedAnalysis.character != null)
		{
			if (_selfAnalysis.isMage
					&& dist2 > _selfAnalysis.maxCastRange * _selfAnalysis.maxCastRange
					&& _actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
							_secondMostHatedAnalysis.character.getY()) < _selfAnalysis.maxCastRange
							* _selfAnalysis.maxCastRange)
			{
				((L2Attackable)_actor).reduceHate(hated[0],
						1 + (((L2Attackable)_actor).getHating(hated[0]) - ((L2Attackable)_actor).getHating(hated[1])));
			}
			else if (dist2 > range * range
					&& _actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
							_secondMostHatedAnalysis.character.getY()) < range * range)
			{
				((L2Attackable)_actor).reduceHate(hated[0],
						1 + (((L2Attackable)_actor).getHating(hated[0]) - ((L2Attackable)_actor).getHating(hated[1])));
			}
		}
		
		// Considering, if bigger range will be attempted
		if ((dist2 < 10000 + combinedCollision * combinedCollision) && !_selfAnalysis.isFighter
				&& !_selfAnalysis.isBalanced
				&& (_selfAnalysis.hasLongRangeSkills || _selfAnalysis.isArcher || _selfAnalysis.isHealer)
				&& (_mostHatedAnalysis.isBalanced || _mostHatedAnalysis.isFighter)
				&& (_mostHatedAnalysis.character.isRooted() || _mostHatedAnalysis.isSlower)
				&& (Config.GEODATA == 2 ? 20 : 12) >= Rnd.get(100) // chance
		)
		{
			int posX = _actor.getX();
			int posY = _actor.getY();
			int posZ = _actor.getZ();
			double distance = Math.sqrt(dist2); // This way, we only do the sqrt if we need it
			
			int signx = -1;
			int signy = -1;
			if (_actor.getX() > _mostHatedAnalysis.character.getX())
				signx = 1;
			if (_actor.getY() > _mostHatedAnalysis.character.getY())
				signy = 1;
			posX += Math.round((float)((signx * ((range / 2) + (Rnd.get(range)))) - distance));
			posY += Math.round((float)((signy * ((range / 2) + (Rnd.get(range)))) - distance));
			setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(posX, posY, posZ, 0));
			return;
		}
		
		// Cannot see target, needs to go closer, currently just goes to range 300 if mage
		if ((dist2 > 96100 + combinedCollision * combinedCollision) // 310
				&& _selfAnalysis.hasLongRangeSkills
				&& !GeoData.getInstance().canSeeTarget(_actor, _mostHatedAnalysis.character))
		{
			if (!(_selfAnalysis.isMage && _actor.isMuted()))
			{
				moveToPawn(_mostHatedAnalysis.character, 300);
				return;
			}
		}
		
		if (_mostHatedAnalysis.character.isMoving())
			range += 50;
		// Check if the actor is far from target
		if (dist2 > range * range)
		{
			if (!_actor.isMuted() && (_selfAnalysis.hasLongRangeSkills || _selfAnalysis.healSkills.length > 0))
			{
				// check for long ranged skills and heal/buff skills
				if (!_mostHatedAnalysis.isCanceled)
				{
					for (L2Skill sk : _selfAnalysis.cancelSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= 8)
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_mostHatedAnalysis.isCanceled = true;
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (_selfAnalysis.lastDebuffTick + 60 < GameTimeManager.getGameTicks())
				{
					for (L2Skill sk : _selfAnalysis.debuffSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						int chance = 8;
						if (_selfAnalysis.isFighter && _mostHatedAnalysis.isMage)
							chance = 3;
						if (_selfAnalysis.isFighter && _mostHatedAnalysis.isArcher)
							chance = 12;
						if (_selfAnalysis.isMage && !_mostHatedAnalysis.isMage)
							chance = 10;
						if (_selfAnalysis.isHealer)
							chance = 12;
						if (_mostHatedAnalysis.isMagicResistant)
							chance /= 2;
						
						if (Rnd.nextInt(100) <= chance)
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_selfAnalysis.lastDebuffTick = GameTimeManager.getGameTicks();
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (!_mostHatedAnalysis.character.isMuted())
				{
					int chance = 8;
					if (!(_mostHatedAnalysis.isMage || _mostHatedAnalysis.isBalanced))
						chance = 3;
					for (L2Skill sk : _selfAnalysis.muteSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= chance)
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (_secondMostHatedAnalysis.character != null && !_secondMostHatedAnalysis.character.isMuted()
						&& (_secondMostHatedAnalysis.isMage || _secondMostHatedAnalysis.isBalanced))
				{
					double secondHatedDist2 =
							_actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
									_secondMostHatedAnalysis.character.getY());
					for (L2Skill sk : _selfAnalysis.muteSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (secondHatedDist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= 2)
						{
							_actor.setTarget(_secondMostHatedAnalysis.character);
							clientStopMoving(null);
							_accessor.doCast(sk);
							_actor.setTarget(_mostHatedAnalysis.character);
							return;
						}
					}
				}
				if (!_mostHatedAnalysis.character.isSleeping())
				{
					for (L2Skill sk : _selfAnalysis.sleepSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= (_selfAnalysis.isHealer ? 10 : 1))
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (_secondMostHatedAnalysis.character != null && !_secondMostHatedAnalysis.character.isSleeping())
				{
					double secondHatedDist2 =
							_actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
									_secondMostHatedAnalysis.character.getY());
					for (L2Skill sk : _selfAnalysis.sleepSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (secondHatedDist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= (_selfAnalysis.isHealer ? 10 : 3))
						{
							_actor.setTarget(_secondMostHatedAnalysis.character);
							clientStopMoving(null);
							_accessor.doCast(sk);
							_actor.setTarget(_mostHatedAnalysis.character);
							return;
						}
					}
				}
				if (!_mostHatedAnalysis.character.isRooted())
				{
					for (L2Skill sk : _selfAnalysis.rootSkills)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= (_mostHatedAnalysis.isSlower ? 3 : 8))
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (!_mostHatedAnalysis.character.isAttackingDisabled())
				{
					for (L2Skill sk : _selfAnalysis.generalDisablers)
					{
						int castRange = sk.getCastRange() + combinedCollision;
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
								|| (dist2 > castRange * castRange))
							continue;
						if (Rnd.nextInt(100) <= ((_selfAnalysis.isFighter && _actor.isRooted()) ? 15 : 7))
						{
							clientStopMoving(null);
							_accessor.doCast(sk);
							_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
							return;
						}
					}
				}
				if (_actor.getStatus().getCurrentHp() < _actor.getMaxHp() * 0.4)
				{
					for (L2Skill sk : _selfAnalysis.healSkills)
					{
						if (_actor.isSkillDisabled(sk.getId())
								|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk))
							continue;
						int chance = 7;
						if (_mostHatedAnalysis.character.isAttackingDisabled())
							chance += 10;
						if (_secondMostHatedAnalysis.character == null
								|| _secondMostHatedAnalysis.character.isAttackingDisabled())
							chance += 10;
						if (Rnd.nextInt(100) <= chance)
						{
							_actor.setTarget(_actor);
							clientStopMoving(null);
							_accessor.doCast(sk);
							_actor.setTarget(_mostHatedAnalysis.character);
							return;
						}
					}
				}
				
				// chance decision for launching long range skills
				int castingChance = 5;
				if (_selfAnalysis.isMage || _selfAnalysis.isHealer)
					castingChance = 50; // mages
				if (_selfAnalysis.isBalanced)
				{
					if (!_mostHatedAnalysis.isFighter) // advance to mages
						castingChance = 15;
					else
						castingChance = 25; // stay away from fighters
				}
				if (_selfAnalysis.isFighter)
				{
					if (_mostHatedAnalysis.isMage)
						castingChance = 3;
					else
						castingChance = 7;
					if (_actor.isRooted())
						castingChance = 20; // doesn't matter if no success first round
				}
				for (L2Skill sk : _selfAnalysis.generalSkills)
				{
					int castRange = sk.getCastRange() + combinedCollision;
					if (_actor.isSkillDisabled(sk.getId())
							|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
							|| (dist2 > castRange * castRange))
						continue;
					
					if (Rnd.nextInt(100) <= castingChance)
					{
						clientStopMoving(null);
						_accessor.doCast(sk);
						_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
						return;
					}
				}
			}
			
			// Move the actor to Pawn server side AND client side by sending Server->Client packet MoveToPawn (broadcast)
			if (_selfAnalysis.isMage && !_actor.isMuted())
			{
				// mages stay a bit further away if not muted or low mana
				if ((_actor.getMaxMp() / 3) < _actor.getStatus().getCurrentMp())
				{
					range = _selfAnalysis.maxCastRange;
					if (dist2 < range * range) // don't move backwards here
						return;
				}
			}
			// healers do not even follow
			if (_selfAnalysis.isHealer)
				return;
			
			if (_mostHatedAnalysis.character.isMoving())
				range -= 100;
			if (range < 5)
				range = 5;
			moveToPawn(_mostHatedAnalysis.character, range);
			return;
		}
		
		// **************************************************
		// In case many mobs are trying to hit from same place, move a bit,
		// circling around the target
		if (Rnd.nextInt(100) <= 33) // check it once per 3 seconds
		{
			for (L2Object nearby : _actor.getKnownList().getKnownCharactersInRadius(10))
			{
				if (nearby instanceof L2Attackable && nearby != _mostHatedAnalysis.character)
				{
					int diffx = Rnd.get(combinedCollision, combinedCollision + 40);
					if (Rnd.get(10) < 5)
						diffx = -diffx;
					int diffy = Rnd.get(combinedCollision, combinedCollision + 40);
					if (Rnd.get(10) < 5)
						diffy = -diffy;
					moveTo(_mostHatedAnalysis.character.getX() + diffx, _mostHatedAnalysis.character.getY() + diffy,
							_mostHatedAnalysis.character.getZ());
					return;
				}
			}
		}
		
		// Calculate a new attack timeout.
		_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
		
		// check for close combat skills && heal/buff skills
		
		if (!_mostHatedAnalysis.isCanceled)
		{
			for (L2Skill sk : _selfAnalysis.cancelSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= 8)
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					_mostHatedAnalysis.isCanceled = true;
					return;
				}
			}
		}
		if (_selfAnalysis.lastDebuffTick + 60 < GameTimeManager.getGameTicks())
		{
			for (L2Skill sk : _selfAnalysis.debuffSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				int chance = 5;
				if (_selfAnalysis.isFighter && _mostHatedAnalysis.isMage)
					chance = 3;
				if (_selfAnalysis.isFighter && _mostHatedAnalysis.isArcher)
					chance = 3;
				if (_selfAnalysis.isMage && !_mostHatedAnalysis.isMage)
					chance = 4;
				if (_selfAnalysis.isHealer)
					chance = 12;
				if (_mostHatedAnalysis.isMagicResistant)
					chance /= 2;
				if (sk.getCastRange() < 200)
					chance += 3;
				if (Rnd.nextInt(100) <= chance)
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					_selfAnalysis.lastDebuffTick = GameTimeManager.getGameTicks();
					return;
				}
			}
		}
		if (!_mostHatedAnalysis.character.isMuted() && (_mostHatedAnalysis.isMage || _mostHatedAnalysis.isBalanced))
		{
			for (L2Skill sk : _selfAnalysis.muteSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= 7)
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					return;
				}
			}
		}
		if (_secondMostHatedAnalysis.character != null && !_secondMostHatedAnalysis.character.isMuted()
				&& (_secondMostHatedAnalysis.isMage || _secondMostHatedAnalysis.isBalanced))
		{
			double secondHatedDist2 =
					_actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
							_secondMostHatedAnalysis.character.getY());
			for (L2Skill sk : _selfAnalysis.muteSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (secondHatedDist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= 3)
				{
					_actor.setTarget(_secondMostHatedAnalysis.character);
					clientStopMoving(null);
					_accessor.doCast(sk);
					_actor.setTarget(_mostHatedAnalysis.character);
					return;
				}
			}
		}
		if (!_mostHatedAnalysis.character.isSleeping() && _selfAnalysis.isHealer)
		{
			for (L2Skill sk : _selfAnalysis.sleepSkills)
			{
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= 10)
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
					return;
				}
			}
		}
		if (_secondMostHatedAnalysis.character != null && !_secondMostHatedAnalysis.character.isSleeping())
		{
			double secondHatedDist2 =
					_actor.getPlanDistanceSq(_secondMostHatedAnalysis.character.getX(),
							_secondMostHatedAnalysis.character.getY());
			for (L2Skill sk : _selfAnalysis.sleepSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (secondHatedDist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= (_selfAnalysis.isHealer ? 10 : 4))
				{
					_actor.setTarget(_secondMostHatedAnalysis.character);
					clientStopMoving(null);
					_accessor.doCast(sk);
					_actor.setTarget(_mostHatedAnalysis.character);
					return;
				}
			}
		}
		if (!_mostHatedAnalysis.character.isRooted() && _mostHatedAnalysis.isFighter && !_selfAnalysis.isFighter)
		{
			for (L2Skill sk : _selfAnalysis.rootSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= (_selfAnalysis.isHealer ? 10 : 4))
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					return;
				}
			}
		}
		if (!_mostHatedAnalysis.character.isAttackingDisabled())
		{
			for (L2Skill sk : _selfAnalysis.generalDisablers)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				int castRange = sk.getCastRange() + combinedCollision;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
						|| (dist2 > castRange * castRange))
					continue;
				if (Rnd.nextInt(100) <= ((sk.getCastRange() < 200) ? 10 : 7))
				{
					clientStopMoving(null);
					_accessor.doCast(sk);
					return;
				}
			}
		}
		if (_actor.getStatus().getCurrentHp() < _actor.getMaxHp() * (_selfAnalysis.isHealer ? 0.7 : 0.4))
		{
			for (L2Skill sk : _selfAnalysis.healSkills)
			{
				if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
					continue;
				if (_actor.isSkillDisabled(sk.getId())
						|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk))
					continue;
				int chance = (_selfAnalysis.isHealer ? 15 : 7);
				if (_mostHatedAnalysis.character.isAttackingDisabled())
					chance += 10;
				if (_secondMostHatedAnalysis.character == null
						|| _secondMostHatedAnalysis.character.isAttackingDisabled())
					chance += 10;
				if (Rnd.nextInt(100) <= chance)
				{
					_actor.setTarget(_actor);
					clientStopMoving(null);
					_accessor.doCast(sk);
					_actor.setTarget(_mostHatedAnalysis.character);
					return;
				}
			}
		}
		for (L2Skill sk : _selfAnalysis.generalSkills)
		{
			if ((_actor.isMuted() && sk.isMagic()) || (_actor.isPhysicalMuted() && !sk.isMagic()))
				continue;
			int castRange = sk.getCastRange() + combinedCollision;
			if (_actor.isSkillDisabled(sk.getId())
					|| _actor.getStatus().getCurrentMp() < _actor.getStat().getMpConsume(sk)
					|| (dist2 > castRange * castRange))
				continue;
			
			// chance decision for launching general skills in melee fight
			// close range skills should be higher, long range lower
			int castingChance = 5;
			if (_selfAnalysis.isMage || _selfAnalysis.isHealer)
			{
				if (sk.getCastRange() < 200)
					castingChance = 35;
				else
					castingChance = 25; // mages
			}
			if (_selfAnalysis.isBalanced)
			{
				if (sk.getCastRange() < 200)
					castingChance = 12;
				else
				{
					if (_mostHatedAnalysis.isMage) // hit mages
						castingChance = 2;
					else
						castingChance = 5;
				}
			}
			if (_selfAnalysis.isFighter)
			{
				if (sk.getCastRange() < 200)
					castingChance = 12;
				else
				{
					if (_mostHatedAnalysis.isMage)
						castingChance = 1;
					else
						castingChance = 3;
				}
			}
			
			if (Rnd.nextInt(100) <= castingChance)
			{
				clientStopMoving(null);
				_accessor.doCast(sk);
				return;
			}
		}
		
		// Finally, physical attacks
		if (!_selfAnalysis.isHealer)
		{
			clientStopMoving(null);
			_accessor.doAttack(_mostHatedAnalysis.character);
		}
	}
	
	/**
	 * Manage AI thinking actions of a L2Attackable.<BR><BR>
	 */
	@Override
	protected void onEvtThink()
	{
		// Check if the thinking action is already in progress
		if (_thinking || _actor.isCastingNow() || _actor.isAllSkillsDisabled())
			return;
		
		// Start thinking action
		_thinking = true;
		
		try
		{
			// Manage AI thinks of a L2Attackable
			if (getIntention() == AI_INTENTION_ACTIVE)
				thinkActive();
			else if (getIntention() == AI_INTENTION_ATTACK)
				thinkAttack();
		}
		finally
		{
			// Stop thinking action
			_thinking = false;
		}
	}
	
	/**
	 * Launch actions corresponding to the Event Attacked.<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Init the attack : Calculate the attack timeout, Set the _globalAggro to 0, Add the attacker to the actor _aggroList</li>
	 * <li>Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player</li>
	 * <li>Set the Intention to AI_INTENTION_ATTACK</li><BR><BR>
	 * 
	 * @param attacker The L2Creature that attacks the actor
	 * 
	 */
	@Override
	protected void onEvtAttacked(L2Creature attacker)
	{
		/*if (_actor instanceof L2ChestInstance && !((L2ChestInstance)_actor).isOpenFailed())
		{
			((L2ChestInstance)_actor).deleteMe();
			((L2ChestInstance)_actor).getSpawn().startRespawn();
		}*/
		
		// Calculate the attack timeout
		_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
		
		// Set the _globalAggro to 0 to permit attack even just after spawn
		if (_globalAggro < 0)
			_globalAggro = 0;
		
		// Add the attacker to the _aggroList of the actor
		if (!_actor.isCoreAIDisabled())
			((L2Attackable)_actor).addDamageHate(attacker, 0, 1);
		
		// Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player
		if (!_actor.isRunning())
			_actor.setRunning();
		
		// Set the Intention to AI_INTENTION_ATTACK
		if (getIntention() != AI_INTENTION_ATTACK && !_actor.isCoreAIDisabled())
		{
			setIntention(CtrlIntention.AI_INTENTION_ATTACK, attacker);
		}
		else if (((L2Attackable)_actor).getMostHated() != getAttackTarget() && !_actor.isCoreAIDisabled())
		{
			setIntention(CtrlIntention.AI_INTENTION_ATTACK, attacker);
		}
		else if (getIntention() != AI_INTENTION_INTERACT && _actor.isCoreAIDisabled())
			setIntention(CtrlIntention.AI_INTENTION_INTERACT, attacker);
		
		super.onEvtAttacked(attacker);
	}
	
	/**
	 * Launch actions corresponding to the Event Aggression.<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Add the target to the actor _aggroList or update hate if already present </li>
	 * <li>Set the actor Intention to AI_INTENTION_ATTACK (if actor is L2GuardInstance check if it isn't too far from its home location)</li><BR><BR>
	 * 
	 * @param target The L2Creature that attacks
	 * @param aggro The value of hate to add to the actor against the target
	 * 
	 */
	@Override
	protected void onEvtAggression(L2Creature target, int aggro)
	{
		L2Attackable me = (L2Attackable)_actor;
		
		if (target != null)
		{
			// Add the target to the actor _aggroList or update hate if already present
			me.addDamageHate(target, 0, aggro);
			
			// Set the actor AI Intention to AI_INTENTION_ATTACK
			if (getIntention() != CtrlIntention.AI_INTENTION_ATTACK)
			{
				// Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player
				if (!_actor.isRunning())
					_actor.setRunning();
				
				setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
			}
		}
	}
	
	@Override
	protected void onIntentionActive()
	{
		// Cancel attack timeout
		_attackTimeout = Integer.MAX_VALUE;
		super.onIntentionActive();
	}
	
	public void setGlobalAggro(int value)
	{
		_globalAggro = value;
	}
	
	@Override
	public void removeReferencesOf(L2Playable playable)
	{
		super.removeReferencesOf(playable);
		
		if (_mostHatedAnalysis.character == playable)
			_mostHatedAnalysis.update(getActor(), null);
		
		if (_secondMostHatedAnalysis.character == playable)
			_secondMostHatedAnalysis.update(getActor(), null);
	}
}
