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
package com.l2jfree.gameserver.network.bridge;

/**
 * One immutable change recorded for a bridge attempt. Kind 1 item, 2 coin, 3 skill, 4 skill points, 5 target.
 * Skill points carry the amount before and the amount after, as well as the delta.
 */
public final class BridgeEffect
{
	public static final int ITEM = 1;
	public static final int COIN = 2;
	public static final int SKILL = 3;
	public static final int SP = 4;
	public static final int TARGET = 5;
	
	private final int _kind;
	private final int _item;
	private final int _object;
	private final long _delta;
	private final int _skill;
	private final long _old;
	private final long _new;
	private final int _target;
	
	private BridgeEffect(int kind, int item, int object, long delta, int skill, long oldValue, long newValue, int target)
	{
		_kind = kind;
		_item = item;
		_object = object;
		_delta = delta;
		_skill = skill;
		_old = oldValue;
		_new = newValue;
		_target = target;
	}
	
	public static BridgeEffect item(int itemId, int objectId, long delta)
	{
		if (delta == 0)
			throw new IllegalArgumentException("delta must not be zero");
		return new BridgeEffect(ITEM, itemId, objectId, delta, 0, 0, 0, 0);
	}
	
	public static BridgeEffect coin(long delta)
	{
		if (delta == 0)
			throw new IllegalArgumentException("delta must not be zero");
		return new BridgeEffect(COIN, 0, 0, delta, 0, 0, 0, 0);
	}
	
	public static BridgeEffect skill(int skillId, int oldLevel, int newLevel)
	{
		if (oldLevel < 0 || newLevel < 0 || oldLevel == newLevel)
			throw new IllegalArgumentException("skill levels");
		if (newLevel == 0 && oldLevel <= 0)
			throw new IllegalArgumentException("skill removal");
		return new BridgeEffect(SKILL, 0, 0, 0, skillId, oldLevel, newLevel, 0);
	}
	
	public static BridgeEffect sp(long delta, long oldValue, long newValue)
	{
		if (delta == 0)
			throw new IllegalArgumentException("delta must not be zero");
		if (oldValue < 0 || newValue < 0 || oldValue == newValue)
			throw new IllegalArgumentException("skill points");
		return new BridgeEffect(SP, 0, 0, delta, 0, oldValue, newValue, 0);
	}
	
	public static BridgeEffect target(int objectId)
	{
		if (objectId < 0)
			throw new IllegalArgumentException("target");
		return new BridgeEffect(TARGET, 0, 0, 0, 0, 0, 0, objectId);
	}
	
	public int kind()
	{
		return _kind;
	}
	
	public int item()
	{
		return _item;
	}
	
	public int object()
	{
		return _object;
	}
	
	public long delta()
	{
		return _delta;
	}
	
	public int skill()
	{
		return _skill;
	}
	
	public long oldValue()
	{
		return _old;
	}
	
	public long newValue()
	{
		return _new;
	}
	
	public int target()
	{
		return _target;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof BridgeEffect))
			return false;
		BridgeEffect other = (BridgeEffect)obj;
		return _kind == other._kind && _item == other._item && _object == other._object && _delta == other._delta
				&& _skill == other._skill && _old == other._old && _new == other._new && _target == other._target;
	}
	
	@Override
	public int hashCode()
	{
		int result = _kind;
		result = 31 * result + _item;
		result = 31 * result + _object;
		result = 31 * result + Long.hashCode(_delta);
		result = 31 * result + _skill;
		result = 31 * result + Long.hashCode(_old);
		result = 31 * result + Long.hashCode(_new);
		result = 31 * result + _target;
		return result;
	}
}
