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
package com.l2jfree.gameserver.model.clan;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.util.L2FastSet;

public final class L2SiegeClan
{
	private final int _clanId;
	private final L2FastSet<L2Npc> _flags = new L2FastSet<L2Npc>().setShared(true);
	private SiegeClanType _type;
	
	public enum SiegeClanType
	{
		OWNER,
		DEFENDER,
		ATTACKER,
		DEFENDER_PENDING
	}
	
	public L2SiegeClan(int clanId, SiegeClanType type)
	{
		_clanId = clanId;
		_type = type;
	}
	
	public int getNumFlags()
	{
		return getFlag().size();
	}
	
	public void addFlag(L2Npc flag)
	{
		getFlag().add(flag);
	}
	
	public boolean removeFlag(L2Npc flag)
	{
		if (flag == null)
			return false;
		
		flag.deleteMe();
		
		return getFlag().remove(flag);
	}
	
	public void removeFlags()
	{
		for (L2Npc flag : getFlag())
			removeFlag(flag);
	}
	
	public int getClanId()
	{
		return _clanId;
	}
	
	public L2FastSet<L2Npc> getFlag()
	{
		return _flags;
	}
	
	/*** get nearest Flag to Object ***/
	public L2Npc getClosestFlag(L2Object obj)
	{
		double closestDistance = Double.MAX_VALUE;
		double distance;
		L2Npc _flag = null;
		
		for (L2Npc flag : getFlag())
		{
			if (flag == null)
				continue;
			distance = Util.calculateDistance(obj, flag, true);
			if (closestDistance > distance)
			{
				closestDistance = distance;
				_flag = flag;
			}
		}
		return _flag;
	}
	
	public SiegeClanType getType()
	{
		return _type;
	}
	
	public void setType(SiegeClanType setType)
	{
		_type = setType;
	}
}
