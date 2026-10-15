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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.model.skills.Env;

/**
 * @author mkizub
 */
class ConditionTargetAggro extends Condition
{
	private final boolean _isAggro;
	
	public ConditionTargetAggro(boolean isAggro)
	{
		_isAggro = isAggro;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		L2Creature target = env.target;
		if (target instanceof L2MonsterInstance)
			return ((L2MonsterInstance)target).isAggressive() == _isAggro;
		if (target instanceof L2Player)
			return ((L2Player)target).getKarma() > 0;
		return false;
	}
}
