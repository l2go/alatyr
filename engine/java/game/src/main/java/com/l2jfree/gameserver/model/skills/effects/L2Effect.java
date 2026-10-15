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
package com.l2jfree.gameserver.model.skills.effects;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.effects.CreatureEffects;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.impl.EffectCharmOfCourage;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.funcs.FuncOwner;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.EffectInfoPacket.EffectInfoPacketList;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.ShortBuffStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.threadmanager.FIFORunnableQueue;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.util.L2Arrays;

public abstract class L2Effect implements FuncOwner, Runnable
{
	protected static final Logger _log = LoggerFactory.getLogger(L2Effect.class);
	
	public static final L2Effect[] EMPTY_ARRAY = new L2Effect[0];
	
	// member _effector is the instance of L2Creature that cast/used the spell/skill that is
	// causing this effect. Do not confuse with the instance of L2Creature that
	// is being affected by this effect.
	private final L2Creature _effector;
	
	// member _effected is the instance of L2Creature that was affected
	// by this effect. Do not confuse with the instance of L2Creature that
	// casted/used this effect.
	private final L2Creature _effected;
	
	// the skill that was used.
	private final L2Skill _skill;
	
	// the current state
	private boolean _isActing = true;
	
	// period, seconds
	private final int _period;
	
	// Effect template
	private final EffectTemplate _template;
	
	// counter
	private int _count;
	
	private ScheduledFuture<?> _currentFuture;
	
	private volatile boolean _inUse = false;
	private volatile boolean _startConditionsCorrect = false;
	
	protected L2Effect(Env env, EffectTemplate template)
	{
		_template = template;
		_skill = env.skill;
		_effected = env.target;
		_effector = env.player;
		_count = template.count;
		
		// Support for retail herbs duration when _effected has a Summon
		int id = _skill.getId();
		int temp = template.period;
		if (2277 < id && id < 2286 || 2512 <= id && id <= 2514)
		{
			if (_effected.getActingSummon() instanceof L2SummonInstance)
			{
				temp /= 2;
			}
		}
		
		if (env.skillMastery)
			temp *= 2;
		
		_period = temp;
		
		startEffect();
	}
	
	/**
	 * Special constructor to "steal" buffs. Must be implemented on
	 * every child class that can be stolen.
	 *
	 * @param env
	 * @param effect
	 */
	protected L2Effect(Env env, L2Effect effect)
	{
		_template = effect._template;
		_skill = env.skill;
		_effected = env.target;
		_effector = env.player;
		_count = effect.getCount();
		_period = _template.period - effect.getTime();
		
		startEffect();
	}
	
	@SuppressWarnings("deprecation")
	private synchronized void startEffect()
	{
		if (_skill.isPvpSkill() && getShowIcon() && _effected instanceof L2Player)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
			sm.addSkillName(this);
			((L2Player)_effected).sendPacket(sm);
		}
		
		scheduleEffect(_period);
		
		_effected.getEffects().addEffect(this);
		
		GlobalRestrictions.effectCreated(this);
	}
	
	private void scheduleEffect(int initialDelay)
	{
		if (_currentFuture != null)
			_currentFuture.cancel(false);
		
		if (_period <= 0 && _count > 1)
			_effected.getEffects().printStackTrace(getStackTypes(), this);
		
		if (_count > 1)
			_currentFuture =
					ThreadPoolManager.getInstance().scheduleAtFixedRate(this, initialDelay * 1000, _period * 1000L);
		else
			_currentFuture = ThreadPoolManager.getInstance().schedule(this, initialDelay * 1000);
	}
	
	public final int getCount()
	{
		return _count;
	}
	
	public final int getTotalCount()
	{
		return _template.count;
	}
	
	public synchronized final void setTiming(int count, int remaining)
	{
		_count = count;
		
		scheduleEffect(remaining);
	}
	
	public final boolean getShowIcon()
	{
		return _template.showIcon;
	}
	
	public final int getPeriod()
	{
		return _period;
	}
	
	public final int getTime()
	{
		return (int)((_period * 1000L - _currentFuture.getDelay(TimeUnit.MILLISECONDS)) / 1000);
	}
	
	/**
	 * Returns the elapsed time of the task.
	 *
	 * @return Time in seconds.
	 */
	public final long getElapsedTaskTime()
	{
		return ((long)getTotalCount() - _count) * _period + getTime();
	}
	
	public final long getTotalTaskTime()
	{
		return (long)getTotalCount() * _period;
	}
	
	public final long getRemainingTaskTime()
	{
		return getTotalTaskTime() - getElapsedTaskTime();
	}
	
	public final boolean isInUse()
	{
		return _inUse;
	}
	
	/**
	 * Must be called from synchronized context from {@link CreatureEffects}.
	 */
	public synchronized final void setInUse(boolean inUse)
	{
		if (_inUse != inUse)
		{
			_inUse = inUse;
			
			// Asynchronous FIFO execution to avoid deadlocks and concurrency
			EFFECT_QUEUE.execute(_inUse ? ON_START : ON_EXIT);
		}
		//else
		//	_effected.getEffects().printStackTrace(getStackTypes(), this);
	}
	
	private static final FIFORunnableQueue<Runnable> EFFECT_QUEUE = new FIFORunnableQueue<Runnable>() {};
	
	private final Runnable ON_START = new Runnable() {
		@Override
		public void run()
		{
			_startConditionsCorrect = onStart();
			
			if (_startConditionsCorrect)
			{
				_effected.addStatFuncs(getStatFuncs());
				_effected.startAbnormalEffect(_template.abnormalEffect | getTypeBasedAbnormalEffect());
				_effected.startSpecialEffect(_template.specialEffect);
			}
			
			if (_effected instanceof L2Player && ShortBuffStatusUpdate.isShortBuff(L2Effect.this))
			{
				// short buff icon for healing potions
				((L2Player)_effected).startShortBuffStatusUpdate(L2Effect.this);
			}
			else if (_effected instanceof L2Playable)
				((L2Playable)_effected).updateEffectIcons();
		}
	};
	
	private final Runnable ON_ACTION_TIME = new Runnable() {
		@Override
		public void run()
		{
			if (_startConditionsCorrect)
			{
				if (onActionTime()) // false causes effect to finish right away
				{
					if (_count > 0)
						return;
				}
			}
			else
			{
				if (_count > 0)
					return;
			}
			
			exit();
		}
	};
	
	private final Runnable ON_EXIT = new Runnable() {
		@Override
		public void run()
		{
			if (_startConditionsCorrect)
			{
				onExit();
				
				_effected.removeStatsOwner(L2Effect.this);
				_effected.stopAbnormalEffect(_template.abnormalEffect | getTypeBasedAbnormalEffect());
				_effected.stopSpecialEffect(_template.specialEffect);
			}
			
			_startConditionsCorrect = false;
			
			if (_effected instanceof L2Player && ShortBuffStatusUpdate.isShortBuff(L2Effect.this))
			{
				// short buff icon for healing potions
				((L2Player)_effected).stopShortBuffStatusUpdate(L2Effect.this);
			}
			else if (_effected instanceof L2Playable)
				((L2Playable)_effected).updateEffectIcons();
		}
	};
	
	private boolean isActing()
	{
		return _isActing;
	}
	
	private void setActing(boolean isActing)
	{
		_isActing = isActing;
	}
	
	public final String[] getStackTypes()
	{
		return _template.stackTypes;
	}
	
	public final boolean stackTypesEqual(L2Effect e)
	{
		for (String stackType1 : getStackTypes())
			for (String stackType2 : e.getStackTypes())
				if (stackType1.equalsIgnoreCase(stackType2))
					return true;
		
		return false;
	}
	
	public final boolean stackTypesEqual(String stackType2)
	{
		for (String stackType1 : getStackTypes())
			if (stackType1.equalsIgnoreCase(stackType2))
				return true;
		
		return false;
	}
	
	public final float getStackOrder()
	{
		return _template.stackOrder;
	}
	
	public final L2Skill getSkill()
	{
		return _skill;
	}
	
	public final L2Creature getEffector()
	{
		return _effector;
	}
	
	public final L2Creature getEffected()
	{
		return _effected;
	}
	
	public final boolean isSelfEffect()
	{
		return _skill.hasSelfEffects();
	}
	
	public final boolean isHerbEffect()
	{
		return _skill.isHerbEffect();
	}
	
	protected final double calc()
	{
		return _template.lambda;
	}
	
	/**
	 * Stop the L2Effect task and send Server->Client update packet.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Cancel the effect in the the abnormal effect map of the L2Creature </li>
	 * <li>Stop the task of the L2Effect, remove it and update client magic icons </li>
	 * <BR>
	 * <BR>
	 */
	@SuppressWarnings("deprecation")
	public final void exit()
	{
		if (_effected.getEffects().removeEffect(this))
		{
			synchronized (this)
			{
				if (isActing())
				{
					if (getShowIcon() && shouldSendExitMessage() && _effected instanceof L2Player)
					{
						SystemMessage sm;
						if (getCount() == 0)
							sm = new SystemMessage(SystemMessageId.S1_HAS_WORN_OFF);
						else if (getSkill().isToggle())
							sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_ABORTED);
						else
							sm = new SystemMessage(SystemMessageId.EFFECT_S1_DISAPPEARED);
						sm.addSkillName(this);
						
						((L2Player)_effected).sendPacket(sm);
					}
					
					_currentFuture.cancel(false);
					
					final L2Skill skill = _skill.getAfterEffectSkill();
					
					if (skill != null)
					{
						_effected.broadcastPacket(new MagicSkillUse(_effected, _effected, skill, 0, 0));
						_effected.broadcastPacket(new MagicSkillLaunched(_effected, skill, _effected));
						
						skill.getEffects(_effected, _effected);
					}
					
					setActing(false);
				}
			}
		}
	}
	
	private boolean shouldSendExitMessage()
	{
		if (!(_effected instanceof L2Player))
			return false;
		
		final L2Effect e = _effected.getFirstEffect(getId());
		
		if (e == null)
			return true;
		
		if (getStackOrder() > e.getStackOrder())
			return true;
		
		return false;
	}
	
	public final boolean tryNegateDebuff()
	{
		return tryNegateDebuff(100);
	}
	
	public final boolean tryNegateDebuff(int chanceInPercent)
	{
		if (canBeNegatedAsDebuff() && Rnd.get(100) < chanceInPercent)
		{
			exit();
			return true;
		}
		
		return false;
	}
	
	private boolean canBeNegatedAsDebuff()
	{
		switch (getId())
		{
			case 4215: // Raid Curse
			case 4515: // Raid Curse
			case 4082: // Poison of Death
			case 5660: // Battlefield Death Syndrome
				return false;
		}
		
		if (!getSkill().canBeDispeled())
			return false;
		
		if (getEffectType() == L2EffectType.ENVIRONMENT)
			return false;
		
		if (getSkill().isDebuff())
			return true;
		
		switch (getSkill().getSkillType())
		{
			case DEBUFF:
			case WEAKNESS:
			case STUN:
			case SLEEP:
			case CONFUSION:
			case MUTE:
			case FEAR:
			case POISON:
			case BLEED:
			case PARALYZE:
			case ROOT:
				return true;
			default:
				return false;
		}
	}
	
	protected int getTypeBasedAbnormalEffect()
	{
		return 0;
	}
	
	/** returns effect type */
	public abstract L2EffectType getEffectType();
	
	protected boolean onStart()
	{
		return true;
	}
	
	/**
	 * <b>WARNING</b>: default value changed to <b>TRUE</b>.<br>
	 * There is no reason to stop normal effects even if it's scheduled over time.
	 *
	 * @return true for continuation of this effect
	 */
	protected boolean onActionTime()
	{
		return true;
	}
	
	protected void onExit()
	{
	}
	
	@Override
	public final void run()
	{
		synchronized (this)
		{
			if (isActing())
			{
				if (_count-- > 0)
				{
					EFFECT_QUEUE.execute(ON_ACTION_TIME);
					return;
				}
			}
		}
		
		exit();
	}
	
	private Func[] _statFuncs;
	
	public final Func[] getStatFuncs()
	{
		if (_statFuncs == null)
		{
			if (_template.funcTemplates == null)
			{
				_statFuncs = Func.EMPTY_ARRAY;
			}
			else
			{
				final Func[] funcs = new Func[_template.funcTemplates.length];
				
				for (int i = 0; i < _template.funcTemplates.length; i++)
					funcs[i] = _template.funcTemplates[i].getFunc(this);
				
				_statFuncs = L2Arrays.compact(funcs);
			}
		}
		
		return _statFuncs;
	}
	
	public final void addPacket(EffectInfoPacketList list)
	{
		if (!_inUse || !getShowIcon())
			return;
		
		if (!isActing())
			return;
		
		// ShortBuffStatusUpdate sent by handler
		if (ShortBuffStatusUpdate.isShortBuff(this))
			return;
		
		switch (getEffectType())
		{
			case SIGNET_GROUND:
				return;
		}
		
		final ScheduledFuture<?> future = _currentFuture;
		if (future == null)
			return;
		
		long time = getRemainingTaskTime();
		
		if (time <= 0 || 86400 <= time)
			time = -1;
		
		_packetTime = (int)time;
		
		list.addEffect(this);
	}
	
	private int _packetTime;
	
	public final int getPacketTime()
	{
		return _packetTime;
	}
	
	public final int getId()
	{
		return _skill.getId();
	}
	
	public final int getLevel()
	{
		return _skill.getLevel();
	}
	
	public final EffectTemplate getEffectTemplate()
	{
		return _template;
	}
	
	@Override
	public final String getFuncOwnerName()
	{
		return _skill.getFuncOwnerName();
	}
	
	@Override
	public final L2Skill getFuncOwnerSkill()
	{
		return _skill.getFuncOwnerSkill();
	}
	
	public boolean canBeStoredInDb()
	{
		if (_skill.isToggle())
			return false;
		
		if (isHerbEffect())
			return false;
		
		switch (getEffectType())
		{
			case HEAL_OVER_TIME:
			case COMBAT_POINT_HEAL_OVER_TIME:
			case HIDE:
				return false;
		}
		
		if (getRemainingTaskTime() < 30)
			return false;
		
		return true;
	}
	
	public final boolean isBuff()
	{
		return _skill.isBuff();
	}
	
	public final boolean isStayAfterDeath()
	{
		if (this instanceof EffectCharmOfCourage)
			return true;
		
		return getSkill().isStayAfterDeath();
	}
	
}
