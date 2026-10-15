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

import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.model.skills.Env;

class ConditionTargetDoorId extends Condition
{
	private final int[] _doorIds;
	
	public ConditionTargetDoorId(int[] id)
	{
		_doorIds = id;
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		if (!(env.target instanceof L2DoorInstance))
			return false;
		
		for (int id : _doorIds)
		{
			if (((L2DoorInstance)env.target).getDoorId() == id)
				return true;
		}
		
		return false;
	}
}
