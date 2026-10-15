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
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2CCHBossInstance;
import com.l2jfree.gameserver.instancemanager.CCHManager;
import com.l2jfree.gameserver.model.entity.CCHSiege;

/**
 * @author savormix
 */
public final class CCHLeaderStatus extends AttackableStatus
{
	public CCHLeaderStatus(L2CCHBossInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		super.reduceHp0(value, attacker, awake, isDOT, isConsume);
		
		final L2Player player = L2Object.getActingPlayer(attacker);
		if (player == null)
			return;
		
		CCHSiege siege = CCHManager.getInstance().getSiege(player.getClan());
		if (siege != null && siege.equals(getActiveChar().getSiege()))
		{
			// several attackers of one clan hit at the same time
			getActiveChar().getDamageTable().merge(player.getClanId(), (int)value, Integer::sum);
		}
	}
	
	@Override
	public L2CCHBossInstance getActiveChar()
	{
		return (L2CCHBossInstance)_activeChar;
	}
}
