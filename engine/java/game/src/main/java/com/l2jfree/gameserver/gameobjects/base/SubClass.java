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
package com.l2jfree.gameserver.gameobjects.base;

import com.l2jfree.Config;

/**
 * Character Sub-Class Definition
 * <BR>
 * Used to store key information about a character's sub-class.
 * 
 * @author Tempy
 */
public final class SubClass
{
	private static final byte _maxLevel = Config.ALT_MAX_SUBCLASS_LEVEL < Experience.MAX_LEVEL
			? Config.ALT_MAX_SUBCLASS_LEVEL : Experience.MAX_LEVEL - 1;
	
	private ClassId _class;
	private long _exp = Experience.LEVEL[40];
	private volatile int _sp = 0;
	private final Object _bridgeStatLock = new Object();
	private byte _level = 40;
	private int _classIndex = 1;
	
	public SubClass(int classId, long exp, int sp, byte level, int classIndex)
	{
		_class = ClassId.values()[classId];
		_exp = exp;
		setSp(sp);
		_level = level;
		_classIndex = classIndex;
	}
	
	public SubClass(int classId, int classIndex)
	{
		// Used for defining a sub class using default values for XP, SP and player level.
		_class = ClassId.values()[classId];
		_classIndex = classIndex;
	}
	
	public SubClass()
	{
		// Used for specifying ALL attributes of a sub class directly,
		// using the preset default values.
	}
	
	public ClassId getClassDefinition()
	{
		return _class;
	}
	
	public int getClassId()
	{
		return _class.ordinal();
	}
	
	public long getExp()
	{
		return _exp;
	}
	
	public int getSp()
	{
		return _sp;
	}
	
	public byte getLevel()
	{
		return _level;
	}
	
	public int getClassIndex()
	{
		return _classIndex;
	}
	
	public void setClassId(int classId)
	{
		_class = ClassId.values()[classId];
	}
	
	public void setExp(long expValue)
	{
		if (expValue > (Experience.LEVEL[_maxLevel + 1] - 1))
			expValue = (Experience.LEVEL[_maxLevel + 1] - 1);
		
		_exp = expValue;
	}
	
	public void setSp(int spValue)
	{
		setSp(spValue, null);
	}

	/** The owning player is resolved before entering the leaf mutation section. */
	public void setSp(int spValue, com.l2jfree.gameserver.gameobjects.L2Player player)
	{
		com.l2jfree.gameserver.gameobjects.L2Player owner = player != null && player.isBridgeSession() ? player : null;
		synchronized (_bridgeStatLock)
		{
			int before = _sp;
			_sp = spValue;
			int after = _sp;
			if (com.l2jfree.gameserver.network.bridge.EffectLedger.isActive())
			{
				if (player == null)
					com.l2jfree.gameserver.network.bridge.EffectLedger.incompleteUnattributedMutation();
				else if (owner != null && before != after)
					com.l2jfree.gameserver.network.bridge.EffectLedger.record(owner,
							com.l2jfree.gameserver.network.bridge.BridgeEffect.sp((long)after - before, before, after));
			}
		}
	}
	
	public void setClassIndex(int classIndex)
	{
		_classIndex = classIndex;
	}
	
	public void setLevel(byte levelValue)
	{
		if (levelValue > _maxLevel)
			levelValue = _maxLevel;
		else if (levelValue < 40)
			levelValue = 40;
		
		_level = levelValue;
	}
	
	public void incLevel()
	{
		if (getLevel() == _maxLevel)
			return;
		
		_level++;
		setExp(Experience.LEVEL[getLevel()]);
	}
	
	public void decLevel()
	{
		if (getLevel() == 40)
			return;
		
		_level--;
		setExp(Experience.LEVEL[getLevel()]);
	}
}
