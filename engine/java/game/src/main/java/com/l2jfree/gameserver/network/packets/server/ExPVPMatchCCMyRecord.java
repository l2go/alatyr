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

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Sent at the beginning of Crateis Cube and each time KP increases.<BR>
 * Time is counted down automatically from the first time this packet is sent.
 * @author savormix
 */
public class ExPVPMatchCCMyRecord extends L2ServerPacket
{
	private static final String _S__FE_8A_EXPVPMATCHCCMYRECORD = "[S] FE:8A ExPVPMatchCCMyRecord";
	
	private final int _kp;
	
	public ExPVPMatchCCMyRecord(int killPts)
	{
		_kp = killPts;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x8a);
		
		writeD(_kp);
	}
	
	@Override
	public String getType()
	{
		return _S__FE_8A_EXPVPMATCHCCMYRECORD;
	}
}
