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
package com.l2jfree.gameserver.gameobjects.status;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2ControllableMobInstance;

/**
 * @author NB4L1
 */
public class ControllableMobStatus extends AttackableStatus
{
	public ControllableMobStatus(L2ControllableMobInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	public L2ControllableMobInstance getActiveChar()
	{
		return (L2ControllableMobInstance)_activeChar;
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		// TODO: review this, because this was a mess, seems like an old copy of CreatureStatus
		/*
		if (awake)
		{
			if (isSleeping())
				stopSleeping(true);
			if (isImmobileUntilAttacked())
				stopImmobileUntilAttacked(true);
		}
		
		i = getCurrentHp() - i;
		
		if (i < 0)
			i = 0;
		
		setCurrentHp(i);
		
		if (getCurrentHp() < 0.5) // Die
		{
			// First die (and calculate rewards), if currentHp < 0,  then overhit may be calculated
			if (_log.isDebugEnabled())
				_log.debug("char is dead.");
			
			stopMove(null);
			
			// Start the doDie process
			doDie(attacker);
			
			// Now reset currentHp to zero
			setCurrentHp(0);
		}
		*/
		
		super.reduceHp0(value, attacker, awake, isDOT, isConsume);
	}
}
