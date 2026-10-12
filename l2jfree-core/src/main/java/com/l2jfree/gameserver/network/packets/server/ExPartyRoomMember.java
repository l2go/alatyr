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

import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Format:(ch) d d[dsdddd]
 * @author Crion/kombat (format)
 * @author Myzreal (implementation)
 */
public class ExPartyRoomMember extends L2ServerPacket
{
	private static final String _S__FE_08_EXPARTYROOMMEMBER = "[S] FE:08 ExPartyRoomMember";
	
	private final L2PartyRoom _room;
	private final boolean _leader;
	
	public ExPartyRoomMember(L2PartyRoom room)
	{
		this(room, false);
	}
	
	public ExPartyRoomMember(L2PartyRoom room, boolean leader)
	{
		_room = room;
		_leader = leader;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xFE);
		writeH(0x08);
		
		writeD(_leader);
		List<L2Player> members = _room.getMembers();
		writeD(members.size());
		L2Player leader = _room.getLeader();
		L2Party party = _room.getParty();
		for (L2Player member : members)
		{
			writeD(member.getObjectId());
			writeS(member.getName());
			writeD(member.getClassId().getId());
			writeD(member.getLevel());
			writeD(MapRegionManager.getInstance().getL2Region(member));
			if (leader == member)
				writeD(0x01);
			else if (party != null && party == member.getParty())
				writeD(0x02);
			else
				writeD(0x00);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_08_EXPARTYROOMMEMBER;
	}
}
