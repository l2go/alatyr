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
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Sent when the party room leader clicks "Exit" or "X" button.
 * Format: (ch) dd
 * @author -Wooden- (format)
 * @author Myzreal (implementation)
 */
public class RequestDismissPartyRoom extends L2ClientPacket
{
	private static final String _C__D0_0A_REQUESTDISMISSPARTYROOM = "[C] D0:0A RequestDismissPartyRoom";
	
	private int _roomId;
	
	//private int _data2;
	
	@Override
	protected void readImpl()
	{
		_roomId = readD();
		/*_data2 = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		L2PartyRoom room = activeChar.getPartyRoom();
		if (room != null && room.getId() == _roomId && room.getLeader() == activeChar)
			PartyRoomManager.getInstance().removeRoom(_roomId);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_0A_REQUESTDISMISSPARTYROOM;
	}
}
