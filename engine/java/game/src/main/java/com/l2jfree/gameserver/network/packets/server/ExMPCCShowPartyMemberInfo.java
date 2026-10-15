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
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Format:(ch) d [sdd]
 * @author  Crion/kombat
 */
public class ExMPCCShowPartyMemberInfo extends L2ServerPacket
{
	private static final String _S__FE_4B_EXMPCCSHOWPARTYMEMBERINFO = "[S] FE:4b ExMPCCShowPartyMemberInfo []";
	
	public ExMPCCShowPartyMemberInfo(L2Party party)
	{
		_party = party;
	}
	
	private final L2Party _party;
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x4b);
		
		writeD(_party.getMemberCount());
		for (L2Player mem : _party.getPartyMembers())
		{
			writeS(mem.getName());
			writeD(mem.getObjectId());
			writeD(mem.getClassId().getId());
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_4B_EXMPCCSHOWPARTYMEMBERINFO;
	}
	
}
