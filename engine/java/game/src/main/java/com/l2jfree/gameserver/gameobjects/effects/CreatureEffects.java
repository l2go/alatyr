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
package com.l2jfree.gameserver.gameobjects.effects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.packets.server.EffectInfoPacket.EffectInfoPacketList;
import com.l2jfree.util.ObjectPool;

/**
 * @author NB4L1
 */
public class CreatureEffects
{
	protected static final Logger _log = LoggerFactory.getLogger(CreatureEffects.class);
	
	protected final L2Creature _owner;
	
	private L2Effect[] _toArray;
	private List<L2Effect> _effects;
	private Map<String, StackQueue> _stackedEffects;
	
	public CreatureEffects(L2Creature owner)
	{
		_owner = owner;
	}
	
	protected L2Creature getOwner()
	{
		return _owner;
	}
	
	private boolean isEmpty()
	{
		return _effects == null || _effects.isEmpty();
	}
	
	private static boolean isActiveBuff(L2Effect e)
	{
		if (e == null)
			return false;
		
		if (!e.getSkill().isBuff() || e.getSkill().isDebuff())
			return false;
		
		if (e.getSkill().isDanceOrSong())
			return false;
		
		switch (e.getEffectType())
		{
			case TRANSFORMATION:
			case ENVIRONMENT:
				return false;
		}
		
		return e.isInUse() && e.getShowIcon();
	}
	
	private static boolean isActiveDance(L2Effect e, boolean isDance, boolean isSong)
	{
		return e != null && e.isInUse() && (isDance && e.getSkill().isDance() || isSong && e.getSkill().isSong());
	}
	
	public final synchronized L2Effect[] getAllEffects()
	{
		if (isEmpty())
			return L2Effect.EMPTY_ARRAY;
		
		if (_toArray == null)
			_toArray = _effects.toArray(new L2Effect[_effects.size()]);
		
		return _toArray;
	}
	
	public final synchronized L2Effect[] getAllEffects(String stackType)
	{
		if (isEmpty())
			return L2Effect.EMPTY_ARRAY;
		
		final StackQueue queue = _stackedEffects.get(stackType);
		
		if (queue == null)
			return L2Effect.EMPTY_ARRAY;
		
		return queue.getAllEffects();
	}
	
	/**
	 * @deprecated don't use it!
	 */
	@Deprecated
	public final synchronized void addEffect(L2Effect newEffect)
	{
		if (_effects == null)
			_effects = new ArrayList<L2Effect>(4);
		
		if (_stackedEffects == null)
			_stackedEffects = new LinkedHashMap<String, StackQueue>(4);
		
		final int newOrder = getOrder(newEffect);
		
		int index;
		for (index = 0; index < _effects.size(); index++)
		{
			final L2Effect e = _effects.get(index);
			
			if (getOrder(e) > newOrder)
				break;
		}
		
		_effects.add(index, newEffect);
		_toArray = null;
		
		for (String stackType : newEffect.getStackTypes())
		{
			StackQueue stackQueue = _stackedEffects.get(stackType);
			
			if (stackQueue == null)
				stackQueue = StackQueue.newInstance(this, stackType);
			
			stackQueue.add(newEffect);
		}
		
		if (isActiveDance(newEffect, true, true))
		{
			if (getDanceCount(true, true) > Config.ALT_DANCES_SONGS_MAX_AMOUNT)
			{
				for (int i = 0; i < _effects.size(); i++)
				{
					final L2Effect e = _effects.get(i);
					
					if (isActiveDance(e, true, true))
					{
						stopStackedEffects(e);
						return;
					}
				}
			}
		}
		else if (isActiveBuff(newEffect))
		{
			if (getBuffCount() > _owner.getMaxBuffCount())
			{
				for (int i = 0; i < _effects.size(); i++)
				{
					final L2Effect e = _effects.get(i);
					
					if (isActiveBuff(e))
					{
						stopStackedEffects(e);
						return;
					}
				}
			}
		}
	}
	
	private static int getOrder(L2Effect e)
	{
		if (e.getSkill().isToggle())
			return 3;
		
		if (e.getSkill().isOffensive())
			return 2;
		
		if (e.getSkill().isDanceOrSong())
			return 1;
		
		return 0;
	}
	
	/**
	 * @deprecated don't use it!
	 */
	@Deprecated
	public final synchronized boolean removeEffect(L2Effect effect)
	{
		final int index = _effects.indexOf(effect);
		if (index < 0)
			return false;
		
		for (String stackType : effect.getStackTypes())
		{
			StackQueue queue = _stackedEffects.get(stackType);
			
			if (queue != null)
				queue.remove(effect);
		}
		_effects.remove(index);
		_toArray = null;
		
		return true;
	}
	
	private static final class StackQueue
	{
		private static final ObjectPool<StackQueue> POOL = new ObjectPool<StackQueue>() {
			@Override
			protected StackQueue create()
			{
				return new StackQueue();
			}
		};
		
		private static StackQueue newInstance(CreatureEffects effects, String stackType)
		{
			StackQueue stackQueue = POOL.get();
			
			stackQueue._queue.clear();
			stackQueue._effects = effects;
			stackQueue._stackType = stackType;
			stackQueue._effects._stackedEffects.put(stackQueue._stackType, stackQueue);
			
			return stackQueue;
		}
		
		private static void recycle(StackQueue stackQueue)
		{
			stackQueue._effects._stackedEffects.remove(stackQueue._stackType);
			stackQueue._stackType = null;
			stackQueue._effects = null;
			stackQueue._queue.clear();
			
			POOL.store(stackQueue);
		}
		
		private final ArrayList<L2Effect> _queue = new ArrayList<L2Effect>(4);
		private CreatureEffects _effects;
		private String _stackType;
		
		private synchronized L2Effect[] getAllEffects()
		{
			return _queue.toArray(new L2Effect[_queue.size()]);
		}
		
		private synchronized void add(final L2Effect effect)
		{
			int index;
			for (index = 0; index < _queue.size(); index++)
			{
				final L2Effect e = _queue.get(index);
				
				if (effect.getStackOrder() >= e.getStackOrder())
					break;
			}
			
			if (index == 0 && !_queue.isEmpty())
				_queue.get(0).setInUse(false);
			
			_queue.add(index, effect);
			
			if (index == 0)
				_queue.get(0).setInUse(true);
			
			// TODO: Config.EFFECT_CANCELING
		}
		
		private synchronized void remove(final L2Effect effect)
		{
			final int index = _queue.indexOf(effect);
			
			if (index == 0)
				_queue.get(0).setInUse(false);
			
			_queue.remove(index);
			
			if (index == 0 && !_queue.isEmpty())
				_queue.get(0).setInUse(true);
			
			if (_queue.isEmpty())
				StackQueue.recycle(this);
		}
		
		private synchronized L2Effect getFirstEffect()
		{
			if (_queue.isEmpty())
				return null;
			
			return _queue.get(0);
		}
		
		private synchronized void stopAllEffects()
		{
			while (!_queue.isEmpty())
				_queue.get(_queue.size() - 1).exit();
		}
	}
	
	/**
	 * For debugging purpose...
	 */
	public final void printStackTrace(String[] stackTypes, L2Effect effect)
	{
		StringBuilder tb = new StringBuilder();
		
		tb.append(_owner);
		
		if (stackTypes != null)
			tb.append(" -> ").append(StringUtils.join(stackTypes, ";"));
		
		if (effect != null)
			tb.append(" -> ").append(effect.getSkill().toString());
		
		_log.warn(tb.toString(), new IllegalStateException());
	}
	
	public final synchronized L2Effect getFirstEffect(L2Skill skill)
	{
		return getFirstEffect(skill.getId());
	}
	
	public final synchronized boolean hasEffect(L2Skill skill)
	{
		return hasEffect(skill.getId());
	}
	
	public final synchronized void stopEffects(L2Skill skill)
	{
		stopEffects(skill.getId());
	}
	
	public final synchronized L2Effect getFirstEffect(int id)
	{
		return getFirstEffect(id, true);
	}
	
	public final synchronized boolean hasEffect(int id)
	{
		return getFirstEffect(id, false) != null;
	}
	
	public final synchronized void stopEffects(int id)
	{
		for (L2Effect e; (e = getFirstEffect(id, false)) != null;)
			e.exit();
	}
	
	private L2Effect getFirstEffect(int id, boolean searchForInUse)
	{
		if (isEmpty())
			return null;
		
		L2Effect notUsedEffect = null;
		
		for (int i = 0; i < _effects.size(); i++)
		{
			final L2Effect e = _effects.get(i);
			
			if (e.getSkill().getId() == id)
			{
				if (e.isInUse() || !searchForInUse)
					return e;
				else if (notUsedEffect == null)
					notUsedEffect = e;
			}
		}
		
		return notUsedEffect;
	}
	
	public final synchronized L2Effect getFirstEffect(L2EffectType tp)
	{
		return getFirstEffect(tp, true);
	}
	
	public final synchronized boolean hasEffect(L2EffectType tp)
	{
		return getFirstEffect(tp, false) != null;
	}
	
	public final synchronized void stopEffects(L2EffectType tp)
	{
		for (L2Effect e; (e = getFirstEffect(tp, false)) != null;)
			e.exit();
	}
	
	private L2Effect getFirstEffect(L2EffectType tp, boolean searchForInUse)
	{
		if (isEmpty())
			return null;
		
		L2Effect notUsedEffect = null;
		
		for (int i = 0; i < _effects.size(); i++)
		{
			final L2Effect e = _effects.get(i);
			
			if (e.getEffectType() == tp)
			{
				if (e.isInUse() || !searchForInUse)
					return e;
				else if (notUsedEffect == null)
					notUsedEffect = e;
			}
		}
		
		return notUsedEffect;
	}
	
	public final synchronized int getBuffCount()
	{
		if (isEmpty())
			return 0;
		
		int buffCount = 0;
		
		for (int i = 0; i < _effects.size(); i++)
		{
			final L2Effect e = _effects.get(i);
			
			if (isActiveBuff(e))
				buffCount++;
		}
		
		return buffCount;
	}
	
	public final synchronized int getDanceCount(boolean dances, boolean songs)
	{
		if (isEmpty())
			return 0;
		
		int danceCount = 0;
		
		for (int i = 0; i < _effects.size(); i++)
		{
			final L2Effect e = _effects.get(i);
			
			if (isActiveDance(e, dances, songs))
				danceCount++;
		}
		
		return danceCount;
	}
	
	public final synchronized void addPacket(EffectInfoPacketList list)
	{
		if (isEmpty())
			return;
		
		for (int i = 0; i < _effects.size(); i++)
		{
			final L2Effect e = _effects.get(i);
			
			e.addPacket(list);
		}
	}
	
	/**
	 * Exits all effects in this CharEffectList
	 */
	public final void stopAllEffects()
	{
		stopAllEffects(false);
	}
	
	public final synchronized void stopAllEffects(boolean stopEffectsThatLastThroughDeathToo)
	{
		if (isEmpty())
			return;
		
		if (stopEffectsThatLastThroughDeathToo)
		{
			while (!_effects.isEmpty())
			{
				stopStackedEffects(_effects.get(_effects.size() - 1));
			}
		}
		else
		{
			for (int index = _effects.size() - 1; index >= 0; index = Math.min(index - 1, _effects.size() - 1))
			{
				final L2Effect e = _effects.get(index);
				
				if (e.isStayAfterDeath())
					continue;
				
				e.exit();
			}
		}
	}
	
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		stopAllEffects(false);
	}
	
	public final void dispelBuff(int skillId, int skillLvl)
	{
		for (L2Effect e : getAllEffects())
		{
			if (e == null)
				continue;
			
			if (e.getId() != skillId || e.getLevel() != skillLvl)
				continue;
			
			if (!isActiveBuff(e) && (!Config.DANCE_CANCEL_BUFF || !isActiveDance(e, true, true)))
				continue;
			
			if (e.getEffectType() == L2EffectType.TRANSFORMATION)
				continue;
			
			if (!e.getSkill().canBeDispeled())
				continue;
			
			stopStackedEffects(e);
			
			e.exit(); // just to be sure
		}
	}
	
	public final void dispelOnAction()
	{
		for (L2Effect e : getAllEffects())
		{
			if (e == null)
				continue;
			
			if (e.getSkill().isDispeledOnAction())
				e.exit();
		}
	}
	
	public final void dispelOnAttack()
	{
		for (L2Effect e : getAllEffects())
		{
			if (e == null)
				continue;
			
			if (e.getSkill().isDispeledOnAttack())
				e.exit();
		}
	}
	
	private void stopStackedEffects(L2Effect e)
	{
		for (String stackType : e.getStackTypes())
		{
			StackQueue queue = _stackedEffects.get(stackType);
			
			if (queue != null)
				queue.stopAllEffects();
		}
	}
	
	public final synchronized L2Effect getFirstEffect(String stackType)
	{
		return getFirstEffect(stackType, true);
	}
	
	public final synchronized L2Effect getFirstEffect(String[] stackTypes)
	{
		return getFirstEffect(stackTypes, true);
	}
	
	public final synchronized boolean hasEffect(String stackType)
	{
		return getFirstEffect(stackType, false) != null;
	}
	
	public final synchronized boolean hasEffect(String[] stackTypes)
	{
		return getFirstEffect(stackTypes, false) != null;
	}
	
	public final synchronized void stopEffects(String stackType)
	{
		if (isEmpty())
			return;
		
		final StackQueue queue = _stackedEffects.get(stackType);
		
		if (queue != null)
			queue.stopAllEffects();
	}
	
	public final synchronized void stopEffects(String[] stackTypes)
	{
		if (isEmpty())
			return;
		
		for (String stackType : stackTypes)
		{
			final StackQueue queue = _stackedEffects.get(stackType);
			
			if (queue != null)
				queue.stopAllEffects();
		}
	}
	
	private L2Effect getFirstEffect(String stackType, boolean searchForInUse)
	{
		if (isEmpty())
			return null;
		
		final StackQueue queue = _stackedEffects.get(stackType);
		
		if (queue == null)
			return null;
		
		return queue.getFirstEffect();
	}
	
	private L2Effect getFirstEffect(String[] stackTypes, boolean searchForInUse)
	{
		if (isEmpty())
			return null;
		
		for (String stackType : stackTypes)
		{
			final StackQueue queue = _stackedEffects.get(stackType);
			
			if (queue == null)
				continue;
			
			final L2Effect e = queue.getFirstEffect();
			
			if (e != null)
				return e;
		}
		
		return null;
	}
}
