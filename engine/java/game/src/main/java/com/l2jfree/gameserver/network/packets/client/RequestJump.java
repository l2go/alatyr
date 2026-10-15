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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ExJumpToLocation;

public final class RequestJump extends L2ClientPacket
{
	private static final String _C__REQUESTJUMP = "[C] D0:53 RequestJump ch";
	
	@Override
	protected void readImpl()
	{
		// trigger packet
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		if (player.tryJump())
			sendPacket(new ExJumpToLocation(player));
		else
			sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTJUMP;
	}
}
