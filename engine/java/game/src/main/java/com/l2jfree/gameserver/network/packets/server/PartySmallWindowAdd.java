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
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class PartySmallWindowAdd extends L2ServerPacket
{
	private static final String _S__4F_PARTYSMALLWINDOWADD = "[S] 4f PartySmallWindowAdd [dddsdddddddddd]";
	
	private final L2Player _member;
	
	public PartySmallWindowAdd(L2Player member)
	{
		_member = member;
	}
	
	@Override
	protected final void writeImpl(L2Client client, L2Player activeChar)
	{
		if (activeChar == null)
			return;
		
		writeC(0x4f);
		writeD(activeChar.getObjectId()); // c3
		writeD(0x00);//writeD(0x04); ?? //c3
		writeD(_member.getObjectId());
		writeS(_member.getName());
		
		writeD((int)_member.getStatus().getCurrentCp()); //c4
		writeD(_member.getMaxCp()); //c4
		
		writeD((int)_member.getStatus().getCurrentHp());
		writeD(_member.getMaxHp());
		writeD((int)_member.getStatus().getCurrentMp());
		writeD(_member.getMaxMp());
		writeD(_member.getLevel());
		writeD(_member.getClassId().getId());
		writeD(0x00);//writeD(0x01); ??
		writeD(0x00);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__4F_PARTYSMALLWINDOWADD;
	}
}
