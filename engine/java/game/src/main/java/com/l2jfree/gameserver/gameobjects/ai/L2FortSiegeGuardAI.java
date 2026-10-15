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

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortBallistaInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortCommanderInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortSiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * This class manages AI of L2Attackable.<BR><BR>
 * 
 */
public class L2FortSiegeGuardAI extends L2CreatureAI implements Runnable
{
	private static final class FortSiegeGuardAiTaskManager extends
			AbstractIterativePeriodicTaskManager<L2FortSiegeGuardAI>
	{
		private static final FortSiegeGuardAiTaskManager _instance = new FortSiegeGuardAiTaskManager();
		
		private static FortSiegeGuardAiTaskManager getInstance()
		{
			return _instance;
		}
		
		private FortSiegeGuardAiTaskManager()
		{
			super(1000);
		}
		
		@Override
		protected void callTask(L2FortSiegeGuardAI task)
		{
			task.run();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "run()";
		}
	}
	
	private static final int MAX_ATTACK_TIMEOUT = 300; // int ticks, i.e. 30 seconds
	
	/** For attack AI, analysis of mob and its targets */
	private final SelfAnalysis _selfAnalysis = new SelfAnalysis();
	//private TargetAnalysis _mostHatedAnalysis = new TargetAnalysis();
	
	/** The delay after which the attacked is stopped */
	private int _attackTimeout;
	
	/** The L2Attackable aggro counter */
	private int _globalAggro;
	
	/** The flag used to indicate that a thinking action is in progress */
	private volatile boolean _thinking; // to prevent recursive thinking
	
	private final int _attackRange;
	
	/**
	 * Constructor of L2AttackableAI.<BR><BR>
	 * 
	 * @param accessor The AI accessor of the L2Creature
	 * 
	 */
	public L2FortSiegeGuardAI(L2Creature.AIAccessor accessor)
	{
		super(accessor);
		
		_selfAnalysis.init(_actor);
		_attackTimeout = Integer.MAX_VALUE;
		_globalAggro = -10; // 10 seconds timeout of ATTACK after respawn
		
		_attackRange = _actor.getPhysicalAttackRange();
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
		if (target == null)
			return false;
		
		// Check if the target isn't another guard, folk or a door
		if (target instanceof L2FortSiegeGuardInstance || target instanceof L2FortCommanderInstance
				|| target instanceof L2FortBallistaInstance || target instanceof L2NpcInstance
				|| target instanceof L2DoorInstance || target instanceof L2Playable)
		{
			L2Player player = null;
			if (target instanceof L2Player)
				player = ((L2Player)target);
			else if (target instanceof L2Summon)
				player = ((L2Summon)target).getOwner();
			if (player == null
					|| (player.getClan() != null && player.getClan().getHasFort() == ((L2Npc)_actor).getFort()
							.getFortId()))
				return false;
		}
		
		// Check if the target isn't invulnerable
		if (target.isInvul())
		{
			// However EffectInvincible requires to check GMs specially
			if (target instanceof L2Player && ((L2Player)target).isGM())
				return false;
			if (target instanceof L2Summon && ((L2Summon)target).getOwner().isGM())
				return false;
		}
		
		// Check if the target isn't dead
		if (target.isAlikeDead())
			return false;
		
		// Get the owner if the target is a summon
		if (target instanceof L2Summon)
		{
			L2Player owner = ((L2Summon)target).getOwner();
			if (_actor.isInsideRadius(owner, 1000, true, false))
				target = owner;
		}
		
		// Check if the target is a L2Playable
		if (target instanceof L2Playable)
		{
			// Check if the target isn't in silent move mode AND too far (>100)
			if (((L2Playable)target).isSilentMoving() && !_actor.isInsideRadius(target, 250, false, false))
				return false;
		}
		// Los Check Here
		return (_actor.isAutoAttackable(target) && GeoData.getInstance().canSeeTarget(_actor, target));
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
		if (_log.isDebugEnabled())
			_log.info("L2SiegeAI.changeIntention(" + intention + ", " + arg0 + ", " + arg1 + ")");
		
		if (intention == AI_INTENTION_IDLE /*|| intention == AI_INTENTION_ACTIVE*/) // active becomes idle if only a summon is present
		{
			// Check if actor is not dead
			if (!_actor.isAlikeDead())
			{
				L2Attackable npc = (L2Attackable)_actor;
				
				// If its _knownPlayer isn't empty set the Intention to AI_INTENTION_ACTIVE
				if (!npc.getKnownList().getKnownPlayers().isEmpty())
					intention = AI_INTENTION_ACTIVE;
				else
					intention = AI_INTENTION_IDLE;
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
		FortSiegeGuardAiTaskManager.getInstance().startTask(this);
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
		
		// Manage the Attack Intention : Stop current Attack (if necessary), Start a new Attack and Launch Think Event
		//if (_actor.getTarget() != null)
		super.onIntentionAttack(target);
	}
	
	/**
	 * Manage AI standard thinks of a L2Attackable (called by onEvtThink).<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Update every 1s the _globalAggro counter to come close to 0</li>
	 * <li>If the actor is Aggressive and can attack, add all autoAttackable L2Creature in its Aggro Range to its _aggroList, chose a target and order to attack it</li>
	 * <li>If the actor  can't attack, order to it to return to its home location</li>
	 * 
	 */
	private void thinkActive()
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
		
		// Add all autoAttackable L2Creature in L2Attackable Aggro Range to its _aggroList with 0 damage and 1 hate
		// A L2Attackable isn't aggressive during 10s after its spawn because _globalAggro is set to -10
		if (_globalAggro >= 0)
		{
			for (L2Creature target : npc.getKnownList().getKnownCharactersInRadius(_attackRange))
			{
				if (target == null)
					continue;
				if (autoAttackCondition(target)) // check aggression
				{
					// Get the hate level of the L2Attackable against this L2Creature target contained in _aggroList
					int hating = npc.getHating(target);
					
					// Add the attacker to the L2Attackable _aggroList with 0 damage and 1 hate
					if (hating == 0)
						npc.addDamageHate(target, 0, 1);
				}
			}
			
			// Chose a target from its aggroList
			L2Creature hated;
			if (_actor.isConfused())
				hated = getAttackTarget(); // Force mobs to attack anybody if confused
			else
				hated = npc.getMostHated();
			
			//_mostHatedAnalysis.Update(hated);
			
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
					setIntention(CtrlIntention.AI_INTENTION_ATTACK, hated, null);
				}
				
				return;
			}
			
		}
		
		// Order to the L2SiegeGuardInstance to return to its home location because there's no target to attack
		if (_actor.getStat().getWalkSpeed() >= 0)
		{
			if (_actor instanceof L2FortSiegeGuardInstance)
				((L2FortSiegeGuardInstance)_actor).returnHome();
			else
				((L2FortCommanderInstance)_actor).returnHome();
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
	 *
	 * TODO: Manage casting rules to healer mobs (like Ant Nurses)
	 *
	 */
	private void thinkAttack()
	{
		if (_log.isDebugEnabled())
			_log.info("L2FortSiegeGuardAI.thinkAttack(); timeout="
					+ (_attackTimeout - GameTimeManager.getGameTicks()));
		
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
		
		L2Creature attackTarget = getAttackTarget();
		// Check if target is dead or if timeout is expired to stop this attack
		if (attackTarget == null || attackTarget.isAlikeDead() || _attackTimeout < GameTimeManager.getGameTicks())
		{
			// Stop hating this target after the attack timeout or if target is dead
			if (attackTarget != null)
			{
				L2Attackable npc = (L2Attackable)_actor;
				npc.stopHating(attackTarget);
			}
			
			// Cancel target and timeout
			_attackTimeout = Integer.MAX_VALUE;
			
			// Set the AI Intention to AI_INTENTION_ACTIVE
			setIntention(AI_INTENTION_ACTIVE, null, null);
			
			_actor.setWalking();
			return;
		}
		
		factionNotifyAndSupport();
		attackPrepare();
	}
	
	private final void factionNotifyAndSupport()
	{
		final L2Creature target = getAttackTarget();
		final String faction_id = ((L2Npc)_actor).getFactionId();
		// Call all L2Object of its Faction inside the Faction Range
		if (faction_id == null || target == null || target.isInvul())
			return;
		
		// Go through all L2Creature that belong to its faction
		//for (L2Creature cha : _actor.getKnownList().getKnownCharactersInRadius(((L2Npc) _actor).getFactionRange()+_actor.getTemplate().getCollisionRadius()))
		for (L2Creature cha : _actor.getKnownList().getKnownCharactersInRadius(1000))
		{
			if (cha == null)
				continue;
			
			if (!(cha instanceof L2Npc))
			{
				if (_selfAnalysis.hasHealOrResurrect && cha instanceof L2Player
						&& ((L2Npc)_actor).getCastle().getSiege().checkIsDefender(((L2Player)cha).getClan()))
				{
					// heal friends
					if (!_actor.isAttackingDisabled() && cha.getStatus().getCurrentHp() < cha.getMaxHp() * 0.6
							&& _actor.getStatus().getCurrentHp() > _actor.getMaxHp() / 2
							&& _actor.getStatus().getCurrentMp() > _actor.getMaxMp() / 2 && cha.isInCombat())
					{
						for (L2Skill sk : _selfAnalysis.healSkills)
						{
							if (_actor.getStatus().getCurrentMp() < sk.getMpConsume())
								continue;
							if (_actor.isSkillDisabled(sk.getId()))
								continue;
							if (!Util.checkIfInRange(sk.getCastRange(), _actor, cha, true))
								continue;
							
							int chance = 5;
							if (chance >= Rnd.get(100)) // chance
								continue;
							if (!GeoData.getInstance().canSeeTarget(_actor, cha))
								break;
							
							L2Object OldTarget = _actor.getTarget();
							_actor.setTarget(cha);
							clientStopMoving(null);
							_accessor.doCast(sk);
							_actor.setTarget(OldTarget);
							return;
						}
					}
				}
				continue;
			}
			
			final L2Npc npc = (L2Npc)cha;
			
			if (!faction_id.equals(npc.getFactionId()))
				continue;
			
			if (npc.getAI() != null) // TODO: possibly check not needed
			{
				if (!npc.isDead()
						&& Math.abs(target.getZ() - npc.getZ()) < 600
						//&& _actor.getAttackByList().contains(getAttackTarget())
						&& (npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE || npc.getAI().getIntention() == CtrlIntention.AI_INTENTION_ACTIVE)
						//limiting aggro for siege guards
						&& target.isInsideRadius(npc, 1500, true, false)
						&& GeoData.getInstance().canSeeTarget(npc, target))
				{
					FactionAggressionNotificationQueue.add(faction_id, npc, target);
				}
				// heal friends
				if (_selfAnalysis.hasHealOrResurrect && !_actor.isAttackingDisabled()
						&& npc.getStatus().getCurrentHp() < npc.getMaxHp() * 0.6
						&& _actor.getStatus().getCurrentHp() > _actor.getMaxHp() / 2
						&& _actor.getStatus().getCurrentMp() > _actor.getMaxMp() / 2 && npc.isInCombat())
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
						if (chance >= Rnd.get(100)) // chance
							continue;
						if (!GeoData.getInstance().canSeeTarget(_actor, npc))
							break;
						L2Object OldTarget = _actor.getTarget();
						clientStopMoving(null);
						_actor.setTarget(npc);
						_accessor.doCast(sk);
						_actor.setTarget(OldTarget);
						return;
					}
				}
			}
		}
	}
	
	private void attackPrepare()
	{
		// Get all information needed to choose between physical or magical attack
		L2Skill[] skills = null;
		double dist_2 = 0;
		int range = 0;
		L2FortSiegeGuardInstance sGuard = (L2FortSiegeGuardInstance)_actor;
		L2Creature attackTarget = getAttackTarget();
		
		if (attackTarget != null)
		{
			_actor.setTarget(attackTarget);
			skills = _actor.getAllSkills();
			dist_2 = _actor.getPlanDistanceSq(attackTarget.getX(), attackTarget.getY());
			range =
					_actor.getPhysicalAttackRange() + _actor.getTemplate().getCollisionRadius()
							+ attackTarget.getTemplate().getCollisionRadius();
			if (attackTarget.isMoving())
				range += 50;
		}
		else
		{
			//_log.warning("AttackableAI: Attack target is NULL.");
			_actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}
		
		// never attack defenders
		if (attackTarget instanceof L2Player
				&& sGuard.getFort().getSiege().checkIsDefender(((L2Player)attackTarget).getClan()))
		{
			// Cancel the target
			sGuard.stopHating(attackTarget);
			_actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}
		
		if (!GeoData.getInstance().canSeeTarget(_actor, attackTarget))
		{
			// Siege guards differ from normal mobs currently:
			// If target cannot seen, don't attack any more
			sGuard.stopHating(attackTarget);
			_actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}
		
		// Check if the actor isn't muted and if it is far from target
		if (!_actor.isMuted() && dist_2 > range * range)
		{
			// check for long ranged skills and heal/buff skills
			for (L2Skill sk : skills)
			{
				int castRange = sk.getCastRange();
				
				if ((dist_2 <= castRange * castRange) && castRange > 70 && !_actor.isSkillDisabled(sk.getId())
						&& _actor.getStatus().getCurrentMp() >= _actor.getStat().getMpConsume(sk) && !sk.isPassive())
				{
					
					L2Object OldTarget = _actor.getTarget();
					if (sk.getSkillType() == L2SkillType.BUFF || sk.getSkillType() == L2SkillType.HEAL)
					{
						boolean useSkillSelf = true;
						if (sk.getSkillType() == L2SkillType.HEAL
								&& _actor.getStatus().getCurrentHp() > (int)(_actor.getMaxHp() / 1.5))
						{
							useSkillSelf = false;
							break;
						}
						if (sk.getSkillType() == L2SkillType.BUFF)
						{
							if (_actor.getEffects().hasEffect(sk))
								useSkillSelf = false;
						}
						if (useSkillSelf)
							_actor.setTarget(_actor);
					}
					
					clientStopMoving(null);
					_accessor.doCast(sk);
					_actor.setTarget(OldTarget);
					return;
				}
			}
			
			// Check if the L2SiegeGuardInstance is attacking, knows the target and can't run
			if (!(_actor.isAttackingNow()) && (_actor.getRunSpeed() == 0)
					&& (_actor.getKnownList().knowsObject(attackTarget)))
			{
				// Cancel the target
				_actor.getKnownList().removeKnownObject(attackTarget);
				_actor.setTarget(null);
				setIntention(AI_INTENTION_IDLE, null, null);
			}
			else
			{
				double dx = _actor.getX() - attackTarget.getX();
				double dy = _actor.getY() - attackTarget.getY();
				double dz = _actor.getZ() - attackTarget.getZ();
				double homeX = attackTarget.getX() - sGuard.getSpawn().getLocx();
				double homeY = attackTarget.getY() - sGuard.getSpawn().getLocy();
				
				// Check if the L2SiegeGuardInstance isn't too far from it's home location
				if ((dx * dx + dy * dy > 10000) && (homeX * homeX + homeY * homeY > 3240000) // 1800 * 1800
						&& (_actor.getKnownList().knowsObject(attackTarget)))
				{
					// Cancel the target
					_actor.getKnownList().removeKnownObject(attackTarget);
					_actor.setTarget(null);
					setIntention(AI_INTENTION_IDLE, null, null);
				}
				else
				// Move the actor to Pawn server side AND client side by sending Server->Client packet MoveToPawn (broadcast)
				{
					// Temporary hack for preventing guards jumping off towers,
					// before replacing this with effective geodata checks and AI modification
					if (dz * dz < 28900) // 170 // normally 130 if guard z coordinates correct
					{
						if (_selfAnalysis.isHealer)
							return;
						if (_selfAnalysis.isMage)
							range = _selfAnalysis.maxCastRange - 50;
						if (attackTarget.isMoving())
							moveToPawn(attackTarget, range - 70);
						else
							moveToPawn(attackTarget, range);
					}
				}
			}
		}
		// Else, if the actor is muted and far from target, just "move to pawn"
		else if (_actor.isMuted() && dist_2 > range * range && !_selfAnalysis.isHealer)
		{
			// Temporary hack for preventing guards jumping off towers,
			// before replacing this with effective geodata checks and AI modification
			double dz = _actor.getZ() - attackTarget.getZ();
			if (dz * dz < 170 * 170) // normally 130 if guard z coordinates correct
			{
				if (_selfAnalysis.isMage)
					range = _selfAnalysis.maxCastRange - 50;
				if (attackTarget.isMoving())
					moveToPawn(attackTarget, range - 70);
				else
					moveToPawn(attackTarget, range);
			}
		}
		// Else, if this is close enough to attack
		else if (dist_2 <= range * range)
		{
			// Force mobs to attack anybody if confused
			L2Creature hated = null;
			if (_actor.isConfused())
				hated = attackTarget;
			else
				hated = ((L2Attackable)_actor).getMostHated();
			
			if (hated == null)
			{
				setIntention(AI_INTENTION_ACTIVE, null, null);
				return;
			}
			if (hated != attackTarget)
				attackTarget = hated;
			
			_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
			
			// check for close combat skills && heal/buff skills
			if (!_actor.isMuted() && Rnd.nextInt(100) <= 5)
			{
				for (L2Skill sk : skills)
				{
					int castRange = sk.getCastRange();
					
					if (castRange * castRange >= dist_2 && !sk.isPassive()
							&& _actor.getStatus().getCurrentMp() >= _actor.getStat().getMpConsume(sk)
							&& !_actor.isSkillDisabled(sk.getId()))
					{
						L2Object OldTarget = _actor.getTarget();
						if (sk.getSkillType() == L2SkillType.BUFF || sk.getSkillType() == L2SkillType.HEAL)
						{
							boolean useSkillSelf = true;
							if (sk.getSkillType() == L2SkillType.HEAL
									&& _actor.getStatus().getCurrentHp() > (int)(_actor.getMaxHp() / 1.5))
							{
								useSkillSelf = false;
								break;
							}
							if (sk.getSkillType() == L2SkillType.BUFF)
							{
								if (_actor.getEffects().hasEffect(sk))
									useSkillSelf = false;
							}
							if (useSkillSelf)
								_actor.setTarget(_actor);
						}
						
						clientStopMoving(null);
						_accessor.doCast(sk);
						_actor.setTarget(OldTarget);
						return;
					}
				}
			}
			// Finally, do the physical attack itself
			if (!_selfAnalysis.isHealer)
				_accessor.doAttack(attackTarget);
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
		// Calculate the attack timeout
		_attackTimeout = MAX_ATTACK_TIMEOUT + GameTimeManager.getGameTicks();
		
		// Set the _globalAggro to 0 to permit attack even just after spawn
		if (_globalAggro < 0)
			_globalAggro = 0;
		
		// Add the attacker to the _aggroList of the actor
		((L2Attackable)_actor).addDamageHate(attacker, 0, 1);
		
		// Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player
		if (!_actor.isRunning())
			_actor.setRunning();
		
		// Set the Intention to AI_INTENTION_ATTACK
		if (getIntention() != AI_INTENTION_ATTACK)
		{
			setIntention(CtrlIntention.AI_INTENTION_ATTACK, attacker, null);
		}
		
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
			
			// Get the hate of the actor against the target
			aggro = me.getHating(target);
			
			if (aggro <= 0)
			{
				if (me.getMostHated() == null)
				{
					_globalAggro = -25;
					me.clearAggroList();
					setIntention(AI_INTENTION_IDLE, null, null);
				}
				return;
			}
			
			// Set the actor AI Intention to AI_INTENTION_ATTACK
			if (getIntention() != CtrlIntention.AI_INTENTION_ATTACK)
			{
				// Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player
				if (!_actor.isRunning())
					_actor.setRunning();
				
				L2FortSiegeGuardInstance sGuard;
				if (_actor instanceof L2FortSiegeGuardInstance)
					sGuard = (L2FortSiegeGuardInstance)_actor;
				else
					sGuard = (L2FortCommanderInstance)_actor;
				double homeX = target.getX() - sGuard.getSpawn().getLocx();
				double homeY = target.getY() - sGuard.getSpawn().getLocy();
				
				// Check if the L2SiegeGuardInstance is not too far from its home location
				if (homeX * homeX + homeY * homeY < 3240000) // 1800 * 1800
					setIntention(CtrlIntention.AI_INTENTION_ATTACK, target, null);
			}
		}
		else
		{
			//currently only for setting lower general aggro
			if (aggro >= 0)
				return;
			
			L2Creature mostHated = me.getMostHated();
			if (mostHated == null)
			{
				_globalAggro = -25;
				return;
			}
			for (L2Creature aggroed : me.getAggroListRP().keySet())
				me.addDamageHate(aggroed, 0, aggro);
			
			aggro = me.getHating(mostHated);
			if (aggro <= 0)
			{
				_globalAggro = -25;
				me.clearAggroList();
				setIntention(AI_INTENTION_IDLE, null, null);
			}
		}
	}
	
	@Override
	protected void onEvtDead()
	{
		stopAITask();
		super.onEvtDead();
	}
	
	@Override
	public void stopAITask()
	{
		FortSiegeGuardAiTaskManager.getInstance().stopTask(this);
		_accessor.detachAI();
	}
	
}
