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
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Packet used to create a party room/update existing room's info.
 * Format:(c) ddddds
 * @author Crion/kombat (format)
 * @author Myzreal (implementation)
 */
public class RequestPartyMatchList extends L2ClientPacket
{
	private static final String _C__80_REQUESTPARTYMATCHLIST = "[C] 80 RequestPartyMatchList";
	
	private int _lootDist;
	private int _maxMembers;
	private int _minLevel;
	private int _maxLevel;
	private int _roomId;
	private String _roomTitle;
	
	@Override
	protected void readImpl()
	{
		_roomId = readD();
		_maxMembers = readD();
		_minLevel = readD();
		_maxLevel = readD();
		_lootDist = readD();
		_roomTitle = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		L2Party party = activeChar.getParty();
		if (party != null && !party.isLeader(activeChar))
		{
			sendAF();
			return;
		}
		
		L2PartyRoom room = activeChar.getPartyRoom();
		if (room == null)
		{
			PartyRoomManager.getInstance().createRoom(activeChar, _minLevel, _maxLevel, _maxMembers, _lootDist,
					_roomTitle);
			sendPacket(SystemMessageId.PARTY_ROOM_CREATED);
		}
		else if (room.getId() == _roomId)
		{
			room.setLootDist(_lootDist);
			room.setMaxMembers(_maxMembers);
			room.setMinLevel(_minLevel);
			room.setMaxLevel(_maxLevel);
			room.setTitle(_roomTitle);
			room.updateRoomStatus(false);
			room.broadcastPacket(SystemMessageId.PARTY_ROOM_REVISED.getSystemMessage());
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__80_REQUESTPARTYMATCHLIST;
	}
}
