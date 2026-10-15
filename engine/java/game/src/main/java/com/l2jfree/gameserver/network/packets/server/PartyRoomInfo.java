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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Format:(c) dddddds
 * @author Myzreal (implementation)
 * @author Crion/kombat (format)
 * @since 1.3 (Gracia Final)
 */
public class PartyRoomInfo extends L2ServerPacket
{
	private static final String _S__9D_PARTYROOMINFO = "[S] 9D PartyRoomInfo";
	
	private final L2PartyRoom _room;
	
	public PartyRoomInfo(L2PartyRoom room)
	{
		_room = room;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x9d);
		
		writeD(_room.getId());
		writeD(_room.getMaxMembers());
		writeD(_room.getMinLevel());
		writeD(_room.getMaxLevel());
		writeD(_room.getLootDist());
		writeD(_room.getLocation()); // region
		writeS(_room.getTitle());
	}
	
	@Override
	public String getType()
	{
		return _S__9D_PARTYROOMINFO;
	}
}
