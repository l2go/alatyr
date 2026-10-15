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
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;

public final class FolkStatus extends NpcStatus
{
	public FolkStatus(L2NpcInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	boolean canReduceHp(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		return false;
	}
	
	@Override
	public void reduceMp(double value)
	{
	}
	
	@Override
	public L2NpcInstance getActiveChar()
	{
		return (L2NpcInstance)_activeChar;
	}
}
