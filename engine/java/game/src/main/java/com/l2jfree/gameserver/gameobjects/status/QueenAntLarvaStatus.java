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

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.QueenAntLarvaInstance;

/**
 * @author hex1r0
 */
public class QueenAntLarvaStatus extends AttackableStatus
{
	public QueenAntLarvaStatus(L2Attackable activeChar)
	{
		super(activeChar);
	}
	
	@Override
	public QueenAntLarvaInstance getActiveChar()
	{
		return (QueenAntLarvaInstance)_activeChar;
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		super.reduceHp0(0, attacker, awake, isDOT, isConsume);
	}
}
