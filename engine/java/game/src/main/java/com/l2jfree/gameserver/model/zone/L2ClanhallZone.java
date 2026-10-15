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
package com.l2jfree.gameserver.model.zone;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.network.packets.server.AgitDecoInfo;

public class L2ClanhallZone extends L2Zone
{
	private ClanHall _clanhall;
	
	@Override
	protected void register()
	{
		_clanhall = ClanHallManager.getInstance().getClanHallById(getClanhallId());
		_clanhall.registerZone(this);
	}
	
	@Override
	protected void onEnter(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			L2Player player = (L2Player)character;
			// Set as in clan hall
			player.setInsideZone(FLAG_CLANHALL, true);
			
			// Send decoration packet
			if (_clanhall != null && _clanhall.getOwnerId() > 0)
				player.sendPacket(new AgitDecoInfo(_clanhall));
		}
		
		super.onEnter(character);
	}
	
	@Override
	protected void onExit(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			// Unset clanhall zone
			character.setInsideZone(FLAG_CLANHALL, false);
		}
		
		super.onExit(character);
	}
}
