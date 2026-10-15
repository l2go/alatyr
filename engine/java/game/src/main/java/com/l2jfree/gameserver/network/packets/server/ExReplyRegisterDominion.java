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
 * @author savormix
 */
public class ExReplyRegisterDominion extends L2ServerPacket
{
	private final int _territoryId;
	private final boolean _joining;
	private final int _clanReq = 0x00;
	private final int _mercReq = 0x00;
	
	public ExReplyRegisterDominion(int terrId, boolean joining)
	{
		_territoryId = terrId;
		_joining = joining;
	}
	
	@Override
	public String getType()
	{
		return "[S] FE:91 ExReplyRegisterDominion";
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x91);
		
		writeD(_territoryId); // Current Territory Id
		writeD(0x00); // unknown
		writeD(_joining);
		writeD(0x01); // unknown
		writeD(_clanReq); // Clan Request
		writeD(_mercReq); // Merc Request
	}
}
