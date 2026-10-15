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
package com.l2jfree.gameserver.model.skills.conditions;

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;

abstract class AbstractConditionActiveEffectId extends Condition
{
	private final int _effectId;
	private final int _minEffectLvl;
	private final int _maxEffectLvl;
	
	AbstractConditionActiveEffectId(String nodeValue)
	{
		final StringTokenizer st = new StringTokenizer(nodeValue, ",");
		
		_effectId = Integer.decode(st.nextToken());
		_minEffectLvl = st.hasMoreTokens() ? Integer.decode(st.nextToken()) : Integer.MIN_VALUE;
		_maxEffectLvl = st.hasMoreTokens() ? Integer.decode(st.nextToken()) : Integer.MAX_VALUE;
	}
	
	@Override
	boolean testImpl(Env env)
	{
		final L2Creature owner = getEffectOwner(env);
		
		if (owner != null)
			for (L2Effect e : owner.getAllEffects())
				if (e != null && e.getId() == _effectId)
					if (_minEffectLvl <= e.getLevel() && e.getLevel() <= _maxEffectLvl)
						return true;
		
		return false;
	}
	
	abstract L2Creature getEffectOwner(Env env);
}
