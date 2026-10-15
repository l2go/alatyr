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

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.skills.Env;

/**
 * @author MrPoke
 */
final class ConditionPlayerHasClanHall extends Condition
{
	private final int[] _clanHall;
	
	public ConditionPlayerHasClanHall(List<Integer> clanHall)
	{
		_clanHall = ArrayUtils.toPrimitive(clanHall.toArray(new Integer[clanHall.size()]), 0);
		
		Arrays.sort(_clanHall);
	}
	
	@Override
	public boolean testImpl(Env env)
	{
		if (!(env.player instanceof L2Player))
			return false;
		
		L2Clan clan = ((L2Player)env.player).getClan();
		if (clan == null)
			return _clanHall.length == 1 && _clanHall[0] == 0;
		
		// All Clan Hall
		if (_clanHall.length == 1 && _clanHall[0] == -1)
			return clan.getHasHideout() > 0;
		
		return Arrays.binarySearch(_clanHall, clan.getHasHideout()) >= 0;
	}
}
