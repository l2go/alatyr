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
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;
import com.l2jfree.gameserver.network.packets.server.RestartResponse;

public final class RequestRestart extends L2ClientPacket
{
	private static final String _C__REQUESTRESTART = "[C] 57 RequestRestart c";
	
	@Override
	protected void readImpl()
	{
		// trigger packet
	}
	
	@Override
	protected void runImpl()
	{
		L2Client client = getClient();
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		if (!activeChar.canLogout(true) || activeChar.isIllegalWaiting())
		{
			sendAF();
			return;
		}
		
		new Disconnection(client, activeChar).store().deleteMe();
		
		// return the client to the authed status
		client.setState(GameClientState.AUTHED);
		
		sendPacket(RestartResponse.PACKET);
		
		// send char list
		sendPacket(new CharSelectionInfo(client));
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTRESTART;
	}
}
