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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Packet is sent when someone joins/leaves a party room,
 * also when player in party room levels up, changes class
 * or joins the leader's party.<BR>
 * Please note that region is <U>never</U> updated in retail.
 * <BR><BR>
 * Format:(ch) d dsdddd
 * @author savormix
 */
public class ExManagePartyRoomMember extends L2ServerPacket
{
	private static final String _S__FE_0A_EXMANAGEPARTYROOMMEMBER = "[S] FE:0A ExManagePartyRoomMember";
	public static final int ADDED = 0x00;
	public static final int MODIFIED = 0x01;
	public static final int REMOVED = 0x02;
	
	private final int _type;
	private final L2Player _member;
	
	public ExManagePartyRoomMember(int changeType, L2Player member)
	{
		_type = changeType;
		_member = member;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xFE);
		writeH(0x0A);
		
		writeD(_type);
		writeD(_member.getObjectId());
		writeS(_member.getName());
		writeD(_member.getClassId().getId());
		writeD(_member.getLevel());
		writeD(MapRegionManager.getInstance().getL2Region(_member));
		writeD(L2PartyRoom.getPartyRoomState(_member));
	}
	
	@Override
	public String getType()
	{
		return _S__FE_0A_EXMANAGEPARTYROOMMEMBER;
	}
}
