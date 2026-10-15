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
package com.l2jfree.gameserver.gameobjects.stat;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Npc;

public class NpcStat extends CreatureStat
{
	// =========================================================
	// Data Field
	
	// =========================================================
	// Constructor
	public NpcStat(L2Npc activeChar)
	{
		super(activeChar);
		
		setLevel(getActiveChar().getTemplate().getLevel());
	}
	
	// =========================================================
	// Method - Public
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	@Override
	public L2Npc getActiveChar()
	{
		return (L2Npc)_activeChar;
	}
	
	@Override
	public final int getMaxHp()
	{
		return super.getMaxHp() * (getActiveChar().isChampion() ? Config.CHAMPION_HP : 1);
	}
	
	@Override
	public float getMovementSpeedMultiplier()
	{
		if (getActiveChar().isRunning())
		{
			int base = getActiveChar().getTemplate().getBaseRunSpd();
			
			if (base == 0)
				return 1;
			
			return getRunSpeed() * 1f / base;
		}
		else
		{
			int base = getActiveChar().getTemplate().getBaseWalkSpd();
			
			if (base == 0)
				return 1;
			
			return getWalkSpeed() * 1f / base;
		}
	}
}
