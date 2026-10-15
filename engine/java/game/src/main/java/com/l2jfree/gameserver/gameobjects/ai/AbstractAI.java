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

import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_ATTACK;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_CAST;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_FOLLOW;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_IDLE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.AutoAttackStart;
import com.l2jfree.gameserver.network.packets.server.AutoAttackStop;
import com.l2jfree.gameserver.network.packets.server.Die;
import com.l2jfree.gameserver.network.packets.server.ExMoveToLocationInAirShip;
import com.l2jfree.gameserver.network.packets.server.MoveToLocation;
import com.l2jfree.gameserver.network.packets.server.MoveToLocationInVehicle;
import com.l2jfree.gameserver.network.packets.server.MoveToPawn;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.StopRotation;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.L2Math;
import com.l2jfree.lang.L2System;

/**
 * Mother class of all objects AI in the world.<BR>
 */
public abstract class AbstractAI implements Ctrl
{
	protected static final Logger _log = LoggerFactory.getLogger(AbstractAI.class);
	
	private static final int FOLLOW_INTERVAL = 1000;
	private static final int ATTACK_FOLLOW_INTERVAL = 500;
	
	private static final class FollowTaskManager extends AbstractIterativePeriodicTaskManager<AbstractAI>
	{
		private static final FollowTaskManager _instance = new FollowTaskManager();
		
		private static FollowTaskManager getInstance()
		{
			return _instance;
		}
		
		private FollowTaskManager()
		{
			super(FOLLOW_INTERVAL);
		}
		
		@Override
		protected void callTask(AbstractAI task)
		{
			task.followTarget();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "followTarget()";
		}
	}
	
	private static final class AttackFollowTaskManager extends AbstractIterativePeriodicTaskManager<AbstractAI>
	{
		private static final AttackFollowTaskManager _instance = new AttackFollowTaskManager();
		
		private static AttackFollowTaskManager getInstance()
		{
			return _instance;
		}
		
		private AttackFollowTaskManager()
		{
			super(ATTACK_FOLLOW_INTERVAL);
		}
		
		@Override
		protected void callTask(AbstractAI task)
		{
			task.followTarget();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "attackFollowTarget()";
		}
	}
	
	private L2Creature _followTarget;
	private int _followRange;
	
	public synchronized final void followTarget()
	{
		double distance = Util.calculateDistance(_actor, _followTarget, false);
		
		// TODO: fix Z axis follow support, moveToLocation needs improvements
		// Does not allow targets to follow on infinite distance -> fix for "follow me bug".
		// if the target is too far (maybe also teleported)
		
		if (distance > 3000)
		{
			if (distance > 6000 || !_actor.getKnownList().knowsObject(_followTarget))
			{
				if (_actor instanceof L2Summon)
					((L2Summon)_actor).setFollowStatus(false);
				
				setIntention(AI_INTENTION_IDLE);
				return;
			}
		}
		
		if (!isInsideActingRadius(_followTarget, distance, _followRange))
			moveToPawn(_followTarget, _followRange);
	}
	
	public final boolean isInsideActingRadius()
	{
		return isInsideActingRadius(_followTarget, Util.calculateDistance(_actor, _followTarget, false), _followRange);
	}
	
	public final boolean isInsideActingRadius(L2Object target, double distance, double range)
	{
		if (target == null)
			return false;
		
		return distance < range;
	}
	
	public final synchronized void startFollow(L2Creature target)
	{
		if (target == null)
		{
			stopFollow();
			return;
		}
		
		_followTarget = target;
		_followRange = 60;
		_followRange += _actor.getTemplate().getCollisionRadius();
		_followRange += _followTarget.getTemplate().getCollisionRadius();
		
		FollowTaskManager.getInstance().startTask(this);
		followTarget();
	}
	
	public final synchronized void startFollow(L2Creature target, int range)
	{
		if (target == null)
		{
			stopFollow();
			return;
		}
		
		_followTarget = target;
		_followRange = range;
		_followRange += _actor.getTemplate().getCollisionRadius();
		_followRange += _followTarget.getTemplate().getCollisionRadius();
		
		AttackFollowTaskManager.getInstance().startTask(this);
		followTarget();
	}
	
	public final synchronized void stopFollow()
	{
		FollowTaskManager.getInstance().stopTask(this);
		AttackFollowTaskManager.getInstance().stopTask(this);
		
		_followTarget = null;
	}
	
	/** The character that this AI manages */
	protected final L2Creature _actor;
	
	/** An accessor for private methods of the actor */
	protected final L2Creature.AIAccessor _accessor;
	
	/** Current long-term intention */
	private CtrlIntention _intention = AI_INTENTION_IDLE;
	/** Current long-term intention parameter */
	private Object _intentionArg0;
	/** Current long-term intention parameter */
	private Object _intentionArg1;
	
	/** Flags about client's state, in order to know which messages to send */
	protected boolean _clientMoving;
	/** Flags about client's state, in order to know which messages to send */
	protected boolean _clientAutoAttacking;
	/** Flags about client's state, in order to know which messages to send */
	protected int _clientMovingToPawnOffset;
	
	/** Different targets this AI maintains */
	private L2Object _target;
	
	/** Different internal state flags */
	private int _moveToPawnTimeout;
	
	/**
	 * Constructor of AbstractAI.<BR>
	 * <BR>
	 * 
	 * @param accessor The AI accessor of the L2Creature
	 */
	protected AbstractAI(L2Creature.AIAccessor accessor)
	{
		_accessor = accessor;
		
		// Get the L2Creature managed by this Accessor AI
		_actor = accessor.getActor();
	}
	
	/**
	 * Return the L2Creature managed by this Accessor AI.<BR>
	 * <BR>
	 */
	@Override
	public L2Creature getActor()
	{
		return _actor;
	}
	
	/**
	 * Return the current Intention.<BR>
	 * <BR>
	 */
	@Override
	public final CtrlIntention getIntention()
	{
		return _intention;
	}
	
	public final SkillUsageRequest getCurrentSkill()
	{
		return _intention == CtrlIntention.AI_INTENTION_CAST ? (SkillUsageRequest)_intentionArg0 : null;
	}
	
	public final L2Skill getCastSkill()
	{
		final SkillUsageRequest currentSkill = getCurrentSkill();
		
		return currentSkill == null ? null : currentSkill.getSkill();
	}
	
	@Deprecated
	public final synchronized void setCastTarget(L2Creature target)
	{
		if (_intention == CtrlIntention.AI_INTENTION_CAST)
			_intentionArg1 = target;
		else
			_log.warn("", new IllegalStateException());
	}
	
	/**
	 * Return the current cast target.<BR>
	 * <BR>
	 */
	public final L2Creature getCastTarget()
	{
		return _intention == CtrlIntention.AI_INTENTION_CAST ? (L2Creature)_intentionArg1 : null;
	}
	
	public final synchronized void setAttackTarget(L2Creature target)
	{
		if (_intention == CtrlIntention.AI_INTENTION_ATTACK)
			_intentionArg0 = target;
		else
			_log.warn("", new IllegalStateException());
	}
	
	/**
	 * Return current attack target.<BR>
	 * <BR>
	 */
	@Override
	public final L2Creature getAttackTarget()
	{
		return _intention == CtrlIntention.AI_INTENTION_ATTACK ? (L2Creature)_intentionArg0 : null;
	}
	
	/**
	 * Set the Intention of this AbstractAI.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method is USED by AI classes</B></FONT><BR>
	 * <BR>
	 * <B><U> Overridden in </U> : </B><BR>
	 * <B>L2AttackableAI</B> : Create an AI Task executed every 1s (if necessary)<BR>
	 * <B>L2PlayerAI</B> : Stores the current AI intention parameters to later restore it if necessary<BR>
	 * <BR>
	 * 
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention
	 * @param arg1 The second parameter of the Intention
	 */
	synchronized void changeIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		_intention = intention;
		_intentionArg0 = arg0;
		_intentionArg1 = arg1;
	}
	
	public final Object getIntentionArg0()
	{
		return _intentionArg0;
	}
	
	public final Object getIntentionArg1()
	{
		return _intentionArg1;
	}
	
	/**
	 * Launch the L2CreatureAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * 
	 * @param intention The new Intention to set to the AI
	 */
	@Override
	public final void setIntention(CtrlIntention intention)
	{
		setIntention(intention, null, null);
	}
	
	/**
	 * Launch the L2CreatureAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * 
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention (optional target)
	 */
	@Override
	public final void setIntention(CtrlIntention intention, Object arg0)
	{
		setIntention(intention, arg0, null);
	}
	
	/**
	 * Launch the L2CreatureAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * 
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention (optional target)
	 * @param arg1 The second parameter of the Intention (optional target)
	 */
	@Override
	public final void setIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		if (_actor.isInProtectedAction())
		{
			// Cancel action client side by sending Server->Client packet ActionFailed to the L2Player actor
			clientActionFailed();
			// Save intention for delayed execution
			saveNextIntention(intention, arg0, arg1);
			return;
		}
		
		// Cancel saved intention as the current one overrides it
		clearNextIntention();
		
		/*
		 if (Config.DEBUG)
		 _log.warning("AbstractAI: setIntention -> " + intention + " " + arg0 + " " + arg1);
		 */
		
		// Stop the follow mode if necessary
		if (intention != AI_INTENTION_FOLLOW && intention != AI_INTENTION_ATTACK)
			stopFollow();
		
		// Launch the onIntention method of the L2CreatureAI corresponding to the new Intention
		switch (intention)
		{
			case AI_INTENTION_IDLE:
				onIntentionIdle();
				break;
			case AI_INTENTION_ACTIVE:
				onIntentionActive();
				break;
			case AI_INTENTION_REST:
				onIntentionRest();
				break;
			case AI_INTENTION_ATTACK:
				onIntentionAttack((L2Creature)arg0);
				break;
			case AI_INTENTION_CAST:
				onIntentionCast((SkillUsageRequest)arg0);
				break;
			case AI_INTENTION_MOVE_TO:
				onIntentionMoveTo((L2CharPosition)arg0);
				break;
			case AI_INTENTION_MOVE_TO_IN_A_BOAT:
				onIntentionMoveToInABoat((L2CharPosition)arg0, (L2CharPosition)arg1);
				break;
			case AI_INTENTION_MOVE_TO_IN_AIR_SHIP:
				onIntentionMoveToInAirShip((L2CharPosition)arg0, (L2CharPosition)arg1);
				break;
			case AI_INTENTION_FOLLOW:
				onIntentionFollow((L2Creature)arg0);
				break;
			case AI_INTENTION_PICK_UP:
				onIntentionPickUp((L2Object)arg0);
				break;
			case AI_INTENTION_INTERACT:
				onIntentionInteract((L2Object)arg0);
				break;
		}
	}
	
	private static final class IntentionCommand
	{
		private final CtrlIntention _crtlIntention;
		private final Object _arg0;
		private final Object _arg1;
		
		private IntentionCommand(CtrlIntention pIntention, Object pArg0, Object pArg1)
		{
			_crtlIntention = pIntention;
			_arg0 = pArg0;
			_arg1 = pArg1;
		}
	}
	
	private IntentionCommand _nextIntention = null;
	
	private final void saveNextIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		if (_actor instanceof L2Playable)
			if (((L2Playable)_actor).getSkillQueueProtectionTime() > System.currentTimeMillis())
				if (intention == AI_INTENTION_CAST)
					if (L2System.equals(intention, _intention))
						if (L2System.equals(arg0, _intentionArg0))
							if (L2System.equals(arg1, _intentionArg1))
								return;
		
		_nextIntention = new IntentionCommand(intention, arg0, arg1);
	}
	
	final void clearNextIntention()
	{
		_nextIntention = null;
	}
	
	final void executeNextIntention()
	{
		// run interrupted or next intention
		final IntentionCommand nextIntention = _nextIntention;
		if (nextIntention != null)
		{
			clearNextIntention();
			
			setIntention(nextIntention._crtlIntention, nextIntention._arg0, nextIntention._arg1);
		}
		else if (getIntention() == AI_INTENTION_CAST)
			setIntention(AI_INTENTION_IDLE);
		
		notifyEvent(CtrlEvent.EVT_THINK);
	}
	
	public final CtrlIntention getNextCtrlIntention()
	{
		final IntentionCommand nextIntention = _nextIntention;
		
		return nextIntention == null ? null : nextIntention._crtlIntention;
	}
	
	/**
	 * Launch the L2CreatureAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character
	 * attack and is stunned, he will attack again after the stunned period)</B></FONT><BR>
	 * <BR>
	 * 
	 * @param evt The event whose the AI must be notified
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt)
	{
		notifyEvent(evt, null, null);
	}
	
	/**
	 * Launch the L2CreatureAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character
	 * attack and is stunned, he will attack again after the stunned period)</B></FONT><BR>
	 * <BR>
	 * 
	 * @param evt The event whose the AI must be notified
	 * @param arg0 The first parameter of the Event (optional target)
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt, Object arg0)
	{
		notifyEvent(evt, arg0, null);
	}
	
	/**
	 * Launch the L2CreatureAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character
	 * attack and is stunned, he will attack again after the stunned period)</B></FONT><BR>
	 * <BR>
	 * 
	 * @param evt The event whose the AI must be notified
	 * @param arg0 The first parameter of the Event (optional target)
	 * @param arg1 The second parameter of the Event (optional target)
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt, Object arg0, Object arg1)
	{
		if (!_actor.isVisible() || !_actor.hasAI())
			return;
		
		/*
		 if (Config.DEBUG)
		 _log.warning("AbstractAI: notifyEvent -> " + evt + " " + arg0 + " " + arg1);
		 */
		
		switch (evt)
		{
			case EVT_THINK:
				if (!_actor.isInProtectedAction())
					onEvtThink();
				break;
			case EVT_ATTACKED:
				onEvtAttacked((L2Creature)arg0);
				break;
			case EVT_AGGRESSION:
				onEvtAggression((L2Creature)arg0, ((Number)arg1).intValue());
				break;
			case EVT_STUNNED:
				onEvtStunned((L2Creature)arg0);
				break;
			case EVT_PARALYZED:
				onEvtParalyzed((L2Creature)arg0);
				break;
			case EVT_SLEEPING:
				onEvtSleeping((L2Creature)arg0);
				break;
			case EVT_ROOTED:
				onEvtRooted((L2Creature)arg0);
				break;
			case EVT_CONFUSED:
				onEvtConfused((L2Creature)arg0);
				break;
			case EVT_MUTED:
				onEvtMuted((L2Creature)arg0);
				break;
			case EVT_READY_TO_ACT:
				//if (!_actor.isCastingNow() && !_actor.isCastingSimultaneouslyNow())
				onEvtReadyToAct();
				break;
			case EVT_USER_CMD:
				onEvtUserCmd(arg0, arg1);
				break;
			case EVT_ARRIVED:
				// happens e.g. from stopmove but we don't process it if we're casting
				//if (!_actor.isCastingNow() && !_actor.isCastingSimultaneouslyNow())
				onEvtArrived();
				break;
			case EVT_ARRIVED_REVALIDATE:
				// this is disregarded if the char is not moving any more
				//if (_actor.isMoving())
				onEvtArrivedRevalidate();
				break;
			case EVT_ARRIVED_BLOCKED:
				onEvtArrivedBlocked((L2CharPosition)arg0);
				break;
			case EVT_FORGET_OBJECT:
				onEvtForgetObject((L2Object)arg0);
				break;
			case EVT_CANCEL:
				onEvtCancel();
				break;
			case EVT_DEAD:
				onEvtDead();
				break;
			case EVT_FAKE_DEATH:
				onEvtFakeDeath();
				break;
			case EVT_FINISH_CASTING:
				onEvtFinishCasting();
				break;
			case EVT_AFRAID:
				// TODO
				break;
			case EVT_BETRAYED:
				// TODO
				break;
			case EVT_LUCKNOBLESSE:
				// TODO
				break;
		}
	}
	
	protected abstract void onIntentionIdle();
	
	protected abstract void onIntentionActive();
	
	protected abstract void onIntentionRest();
	
	protected abstract void onIntentionAttack(L2Creature target);
	
	protected abstract void onIntentionCast(SkillUsageRequest request);
	
	protected abstract void onIntentionMoveTo(L2CharPosition destination);
	
	protected abstract void onIntentionMoveToInABoat(L2CharPosition destination, L2CharPosition origin);
	
	protected abstract void onIntentionMoveToInAirShip(L2CharPosition destination, L2CharPosition origin);
	
	protected abstract void onIntentionFollow(L2Creature target);
	
	protected abstract void onIntentionPickUp(L2Object item);
	
	protected abstract void onIntentionInteract(L2Object object);
	
	protected abstract void onEvtThink();
	
	protected abstract void onEvtAttacked(L2Creature attacker);
	
	protected abstract void onEvtAggression(L2Creature target, int aggro);
	
	protected abstract void onEvtStunned(L2Creature attacker);
	
	protected abstract void onEvtParalyzed(L2Creature attacker);
	
	protected abstract void onEvtSleeping(L2Creature attacker);
	
	protected abstract void onEvtRooted(L2Creature attacker);
	
	protected abstract void onEvtConfused(L2Creature attacker);
	
	protected abstract void onEvtMuted(L2Creature attacker);
	
	protected abstract void onEvtReadyToAct();
	
	protected abstract void onEvtUserCmd(Object arg0, Object arg1);
	
	protected abstract void onEvtArrived();
	
	protected abstract void onEvtArrivedRevalidate();
	
	protected abstract void onEvtArrivedBlocked(L2CharPosition blocked_at_pos);
	
	protected abstract void onEvtForgetObject(L2Object object);
	
	protected abstract void onEvtCancel();
	
	protected abstract void onEvtDead();
	
	protected abstract void onEvtFakeDeath();
	
	protected abstract void onEvtFinishCasting();
	
	/**
	 * Cancel action client side by sending Server->Client packet ActionFailed to the L2Player actor.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void clientActionFailed()
	{
		if (_actor instanceof L2Player)
			_actor.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Move the actor to Pawn server side AND client side by sending Server->Client packet MoveToPawn <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void moveToPawn(L2Object pawn, int offset)
	{
		// Check if actor can move
		if (!_actor.isMovementDisabled())
		{
			int minOffset = _actor.getTemplate().getCollisionRadius();
			if (pawn instanceof L2Creature)
				minOffset += ((L2Creature)pawn).getTemplate().getCollisionRadius();
			
			offset = L2Math.max(10, offset, minOffset);
			
			// prevent possible extra calls to this function (there is none?),
			// also don't send movetopawn packets too often
			boolean sendPacket = true;
			if (_clientMoving && getTarget() == pawn)
			{
				if (_clientMovingToPawnOffset == offset)
				{
					if (GameTimeManager.getGameTicks() < _moveToPawnTimeout)
						return;
					sendPacket = false;
				}
				else if (_actor.isOnGeodataPath())
				{
					// minimum time to calculate new route is 2 seconds
					if (GameTimeManager.getGameTicks() < (_moveToPawnTimeout + 10))
						return;
				}
			}
			
			// Set AI movement data
			_clientMoving = true;
			_clientMovingToPawnOffset = offset;
			setTarget(pawn);
			_moveToPawnTimeout = GameTimeManager.getGameTicks();
			_moveToPawnTimeout += /*1000*/200 / GameTimeManager.MILLIS_IN_TICK;
			
			if (pawn == null || _accessor == null)
				return;
			
			// if the target runs towards the character then don't force the actor to run over it
			if (pawn instanceof L2Creature && pawn.isMoving())
			{
				double speed = ((L2Creature)pawn).getStat().getMoveSpeed() / GameTimeManager.TICKS_PER_SECOND;
				
				offset += speed * Math.cos(Math.toRadians(Util.getAngleDifference(_actor, pawn)));
			}
			
			// Calculate movement data for a move to location action and add the actor to movingObjects of GameTimeController
			_accessor.moveTo(pawn.getX(), pawn.getY(), pawn.getZ(), offset);
			
			if (!_actor.isMoving())
			{
				_actor.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			// Send a Server->Client packet MoveToPawn/MoveToLocation to the actor and all L2Player in its _knownPlayers
			if (pawn instanceof L2Creature)
			{
				if (_actor.isOnGeodataPath())
				{
					_actor.broadcastPacket(new MoveToLocation(_actor));
					_clientMovingToPawnOffset = 0;
				}
				else if (sendPacket) // don't repeat unnecessarily
					_actor.broadcastPacket(new MoveToPawn(_actor, (L2Creature)pawn, _clientMovingToPawnOffset));
			}
			else
				_actor.broadcastPacket(new MoveToLocation(_actor));
		}
		else
		{
			clientActionFailed();
		}
	}
	
	/**
	 * Move the actor to Location (x,y,z) server side AND client side by sending Server->Client packet MoveToLocation
	 * <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void moveTo(int x, int y, int z)
	{
		// Check if actor can move
		if (!_actor.isMovementDisabled())
		{
			// Set AI movement data
			_clientMoving = true;
			_clientMovingToPawnOffset = 0;
			
			// Calculate movement data for a move to location action and add the actor to movingObjects of GameTimeController
			_accessor.moveTo(x, y, z);
			
			// Send a Server->Client packet MoveToLocation to the actor and all L2Player in its _knownPlayers
			MoveToLocation msg = new MoveToLocation(_actor);
			_actor.broadcastPacket(msg);
			
		}
		else
		{
			clientActionFailed();
		}
	}
	
	protected void moveToInABoat(L2CharPosition destination, L2CharPosition origin)
	{
		// Chek if actor can move
		if (!_actor.isMovementDisabled())
		{
			/*  // Set AI movement data
			 _clientMoving = true;
			 _clientMoving_to_pawn_offset = 0;

			 // Calculate movement data for a move to location action and add the actor to movingObjects of GameTimeController
			 _accessor.moveTo(((L2Player)_actor).getBoat().getX() - destination.x,((L2Player)_actor).getBoat().getY()- destination.y,((L2Player)_actor).getBoat().getZ() - destination.z);
			 */
			// Send a Server->Client packet MoveToLocation to the actor and all L2Player in its _knownPlayers
			//CharMoveToLocation msg = new MoveToLocation(_actor);
			if (((L2Player)_actor).getBoat() != null)
			{
				MoveToLocationInVehicle msg = new MoveToLocationInVehicle((L2Player)_actor, destination, origin);
				_actor.broadcastPacket(msg);
			}
			
		}
		else
		{
			clientActionFailed();
		}
	}
	
	protected void moveToInAirShip(L2CharPosition destination, L2CharPosition origin)
	{
		// Check if actor can move
		if (!_actor.isMovementDisabled())
		{
			/*	// Set AI movement data
			 _client_moving = true;
			 _client_moving_to_pawn_offset = 0;

			 // Calculate movement data for a move to location action and add the actor to movingObjects of GameTimeController
			 _accessor.moveTo(((L2Player)_actor).getBoat().getX() - destination.x,((L2Player)_actor).getBoat().getY()- destination.y,((L2Player)_actor).getBoat().getZ() - destination.z);
			 */
			// Send a Server->Client packet CharMoveToLocation to the actor and all L2Player in its _knownPlayers
			//CharMoveToLocation msg = new CharMoveToLocation(_actor);
			if (((L2Player)_actor).getAirShip() != null)
			{
				ExMoveToLocationInAirShip msg = new ExMoveToLocationInAirShip((L2Player)_actor, destination);
				_actor.broadcastPacket(msg);
			}
			
		}
		else
		{
			_actor.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	/**
	 * Stop the actor movement server side AND client side by sending Server->Client packet StopMove/StopRotation
	 * <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void clientStopMoving(L2CharPosition pos)
	{
		/*
		 if (Config.DEBUG)
		 _log.warning("clientStopMoving();");
		 */
		
		// Stop movement of the L2Creature
		if (_actor.isMoving())
			_accessor.stopMove(pos);
		
		_clientMovingToPawnOffset = 0;
		
		if (_clientMoving || pos != null)
		{
			_clientMoving = false;
			
			// Send a Server->Client packet StopMove to the actor and all L2Player in its _knownPlayers
			StopMove msg = new StopMove(_actor);
			_actor.broadcastPacket(msg);
			
			if (pos != null)
			{
				// Send a Server->Client packet StopRotation to the actor and all L2Player in its _knownPlayers
				StopRotation sr = new StopRotation(_actor.getObjectId(), pos.heading, 0);
				_actor.broadcastPacket(sr);
			}
		}
	}
	
	// Client has already arrived to target, no need to force StopMove packet
	protected void clientStoppedMoving()
	{
		if (_clientMovingToPawnOffset > 0) // movetoPawn needs to be stopped
		{
			_clientMovingToPawnOffset = 0;
			StopMove msg = new StopMove(_actor);
			_actor.broadcastPacket(msg);
		}
		_clientMoving = false;
	}
	
	public boolean isAutoAttacking()
	{
		return _clientAutoAttacking;
	}
	
	public void setAutoAttacking(boolean isAutoAttacking)
	{
		_clientAutoAttacking = isAutoAttacking;
		if (_actor instanceof L2Summon)
		{
			L2Summon summon = (L2Summon)_actor;
			if (summon.getOwner() != null)
				summon.getOwner().getAI().setAutoAttacking(isAutoAttacking);
			return;
		}
	}
	
	/**
	 * Start the actor Auto Attack client side by sending Server->Client packet AutoAttackStart <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	public void clientStartAutoAttack()
	{
		if (_actor instanceof L2Summon)
		{
			L2Summon summon = (L2Summon)_actor;
			if (summon.getOwner() != null)
				summon.getOwner().getAI().clientStartAutoAttack();
			return;
		}
		if (!isAutoAttacking())
		{
			if (_actor instanceof L2Player && ((L2Player)_actor).getPet() != null)
				((L2Player)_actor).getPet().broadcastPacket(
						new AutoAttackStart(((L2Player)_actor).getPet().getObjectId()));
			// Send a Server->Client packet AutoAttackStart to the actor and all L2Player in its _knownPlayers
			_actor.broadcastPacket(new AutoAttackStart(_actor.getObjectId()));
			setAutoAttacking(true);
		}
		AttackStanceTaskManager.getInstance().addAttackStanceTask(_actor);
	}
	
	/**
	 * Stop the actor auto-attack client side by sending Server->Client packet AutoAttackStop <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	public void clientStopAutoAttack()
	{
		if (_actor instanceof L2Summon)
		{
			L2Summon summon = (L2Summon)_actor;
			if (summon.getOwner() != null)
				summon.getOwner().getAI().clientStopAutoAttack();
			return;
		}
		if (_actor instanceof L2Player)
		{
			if (!AttackStanceTaskManager.getInstance().getAttackStanceTask(_actor) && isAutoAttacking())
				AttackStanceTaskManager.getInstance().addAttackStanceTask(_actor);
		}
		else if (isAutoAttacking())
		{
			_actor.broadcastPacket(new AutoAttackStop(_actor.getObjectId()));
			setAutoAttacking(false);
		}
	}
	
	/**
	 * Kill the actor client side by sending Server->Client packet AutoAttackStop, StopMove/StopRotation, Die
	 * <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void clientNotifyDead()
	{
		// Send a Server->Client packet Die to the actor and all L2Player in its _knownPlayers
		Die msg = new Die(_actor);
		_actor.broadcastPacket(msg);
		
		// Init AI
		changeIntention(AI_INTENTION_IDLE, null, null);
		setTarget(null);
		
		// Cancel the follow task if necessary
		stopFollow();
	}
	
	/**
	 * Update the state of this actor client side by sending Server->Client packet MoveToPawn/MoveToLocation and
	 * AutoAttackStart to the L2Player player.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 * 
	 * @param player The L2PcIstance to notify with state of this L2Creature
	 */
	public void describeStateToPlayer(L2Player player)
	{
		if (_clientMoving)
		{
			if (_clientMovingToPawnOffset != 0 && _followTarget != null)
			{
				// Send a Server->Client packet MoveToPawn to the actor and all L2Player in its _knownPlayers
				MoveToPawn msg = new MoveToPawn(_actor, _followTarget, _clientMovingToPawnOffset);
				player.sendPacket(msg);
			}
			else
			{
				// Send a Server->Client packet MoveToLocation to the actor and all L2Player in its _knownPlayers
				MoveToLocation msg = new MoveToLocation(_actor);
				player.sendPacket(msg);
			}
		}
	}
	
	public L2Creature getFollowTarget()
	{
		return _followTarget;
	}
	
	public void setFollowTarget(L2Creature cha)
	{
		_followTarget = cha;
	}
	
	public L2Object getTarget()
	{
		return _target;
	}
	
	public synchronized void setTarget(L2Object target)
	{
		_target = target;
	}
	
	public void removeReferencesOf(L2Playable playable)
	{
		if (_intentionArg0 == playable)
			_intentionArg0 = null;
		
		if (_intentionArg1 == playable)
			_intentionArg1 = null;
		
		if (getTarget() == playable)
			setTarget(null);
		
		if (getFollowTarget() == playable)
			setFollowTarget(null);
	}
}
