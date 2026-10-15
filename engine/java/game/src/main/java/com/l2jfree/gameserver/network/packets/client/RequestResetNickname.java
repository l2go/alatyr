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

/**
 * @author evill33t & savormix
 */
public class RequestResetNickname extends L2ClientPacket
{
	private static final String _C__D0_50_REQUESTRESETNICKNAME = "[C] D0:50 RequestResetNickname";
	
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
		
		if (player.getAppearance().getNickColor() != -1)
		{
			player.getAppearance().setNickColor(-1);
			player.broadcastUserInfo();
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_50_REQUESTRESETNICKNAME;
	}
}
