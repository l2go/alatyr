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

import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format: (ch) dddd
 * @author Myzreal
 */
public class RequestPartyMatchDetail extends L2ClientPacket
{
	private static final String _C__81_REQUESTPARTYMATCHDETAIL = "[C] 81 RequestPartyMatchDetail";
	
	// manual join
	private int _roomId;
	// auto join
	private int _region;
	private boolean _allLevels;
	
	//private int		_data2;
	
	@Override
	protected void readImpl()
	{
		_roomId = readD();
		_region = readD();
		_allLevels = readD() == 1;
		/*_data2 = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getPartyRoom() != null || activeChar.getParty() != null)
		{
			requestFailed(SystemMessageId.PARTY_ROOM_FORBIDDEN);
			return;
		}
		
		activeChar.setPartyMatchingRegion(_region);
		activeChar.setPartyMatchingLevelRestriction(_allLevels);
		
		if (_roomId > 0)
		{
			L2PartyRoom room = PartyRoomManager.getInstance().getPartyRoom(_roomId);
			L2PartyRoom.tryJoin(activeChar, room, false);
		}
		else
		{
			List<L2PartyRoom> list = PartyRoomManager.getInstance().getRooms(activeChar);
			for (L2PartyRoom room : list)
			{
				if (room.canJoin(activeChar) && room.addMember(activeChar))
					break;
			}
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__81_REQUESTPARTYMATCHDETAIL;
	}
}
