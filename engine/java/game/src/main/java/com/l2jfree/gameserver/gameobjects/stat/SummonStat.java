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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;

public class SummonStat extends PlayableStat
{
	// =========================================================
	// Data Field
	
	// =========================================================
	// Constructor
	public SummonStat(L2Summon activeChar)
	{
		super(activeChar);
	}
	
	// =========================================================
	// Method - Public
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	@Override
	public L2Summon getActiveChar()
	{
		return (L2Summon)_activeChar;
	}
	
	@Override
	public byte getAttackElement()
	{
		final L2Player owner = getActiveChar().getOwner();
		
		if (owner == null || !owner.getStat().summonShouldHaveAttackElemental(getActiveChar()))
			return super.getAttackElement();
		
		return owner.getAttackElement();
	}
	
	@Override
	public int getAttackElementValue(byte attribute)
	{
		final L2Player owner = getActiveChar().getOwner();
		
		if (owner == null || !owner.getStat().summonShouldHaveAttackElemental(getActiveChar()))
			return super.getAttackElementValue(attribute);
		
		// 80% of the original value, this method call returns already 20%
		return owner.getAttackElementValue(attribute) * 4;
	}
	
	@Override
	public int getDefenseElementValue(byte attribute)
	{
		final L2Player owner = getActiveChar().getOwner();
		
		if (owner == null)
			return super.getDefenseElementValue(attribute);
		
		// bonus from owner
		return super.getDefenseElementValue(attribute) + owner.getDefenseElementValue(attribute);
	}
}
