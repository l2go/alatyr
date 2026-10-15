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
package com.l2jfree.gameserver.model.restriction;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author Noctarius
 */
public enum AvailableRestriction
{
	// Restrictions can be applied to players
	PlayerUnmount,
	PlayerCast,
	PlayerTeleport,
	PlayerScrollTeleport,
	PlayerGotoLove,
	PlayerSummonFriend,
	PlayerChat() {
		@Override
		public void activatedOn(L2Player player)
		{
			player.sendMessage("You have been chat banned.");
			player.sendEtcStatusUpdate();
		}
		
		@Override
		public void deactivatedOn(L2Player player)
		{
			player.sendPacket(SystemMessageId.CHATBAN_REMOVED);
			player.sendEtcStatusUpdate();
		}
	},
	;
	
	private AvailableRestriction()
	{
	}
	
	public void activatedOn(L2Player player)
	{
	}
	
	public void deactivatedOn(L2Player player)
	{
	}
	
	public static final AvailableRestriction forName(String name)
	{
		for (AvailableRestriction restriction : AvailableRestriction.values())
			if (restriction.name().equals(name))
				return restriction;
		
		return null;
	}
}
