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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.model.skills.Env;

final class ConditionTargetRace extends Condition
{
	private final Race _race;
	
	public ConditionTargetRace(Race race)
	{
		_race = race;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		if (!(env.target instanceof L2Player))
			return false;
		
		return ((L2Player)env.target).getRace() == _race;
	}
}
