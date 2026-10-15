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
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeFlagInstance;

/**
 * @author NB4L1
 */
public final class SiegeFlagStatus extends NpcStatus
{
	public SiegeFlagStatus(L2SiegeFlagInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	public L2SiegeFlagInstance getActiveChar()
	{
		return (L2SiegeFlagInstance)_activeChar;
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		// Advanced Headquarters have double HP.
		if (getActiveChar().isAdvanced())
			value /= 2;
		
		super.reduceHp0(value, attacker, awake, isDOT, isConsume);
		
		if (getActiveChar().getCastle() != null && getActiveChar().getCastle().getSiege().getIsInProgress())
		{
			getActiveChar().flagAttacked();
		}
		else if (getActiveChar().getFort() != null && getActiveChar().getFort().getSiege().getIsInProgress())
		{
			getActiveChar().flagAttacked();
		}
	}
}
