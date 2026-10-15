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
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;

/**
 * @author NB4L1
 */
public final class GrandBossStatus extends AttackableStatus
{
	public GrandBossStatus(L2GrandBossInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	public L2GrandBossInstance getActiveChar()
	{
		return (L2GrandBossInstance)_activeChar;
	}
	
	@Override
	boolean canReduceHp(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		// [L2J_JP ADD SANDMAN]
		if (getActiveChar().isInSocialAction())
			return false;
		
		return super.canReduceHp(value, attacker, awake, isDOT, isConsume);
	}
}
