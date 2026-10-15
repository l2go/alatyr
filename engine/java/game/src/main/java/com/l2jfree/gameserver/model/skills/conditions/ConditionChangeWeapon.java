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
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.skills.Env;

/**
 * @author nBd
 */
class ConditionChangeWeapon extends Condition
{
	private final boolean _required;
	
	/**
	 * 
	 */
	public ConditionChangeWeapon(boolean required)
	{
		_required = required;
	}
	
	/**
	 * @see com.l2jfree.gameserver.model.skills.conditions.Condition#testImpl(com.l2jfree.gameserver.model.skills.Env)
	 */
	@Override
	boolean testImpl(Env env)
	{
		if (!(env.player instanceof L2Player))
			return false;
		
		if (_required)
		{
			L2Weapon weaponItem = env.player.getActiveWeaponItem();
			
			if (weaponItem == null)
				return false;
			
			if (weaponItem.getChangeWeaponId() == 0)
				return false;
		}
		return true;
	}
	
}
