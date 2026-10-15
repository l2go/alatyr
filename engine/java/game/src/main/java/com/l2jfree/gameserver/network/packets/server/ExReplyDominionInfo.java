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
 * A reply packet sent after clients sends RequestDominionInfo.
 * @author savormix
 */
public class ExReplyDominionInfo extends L2ServerPacket
{
	private static final String _S__FE_92_EXREPLYDOMINIONINFO = "[S] FE:92 ExReplyDominionInfo";
	private static final String[] DOM = { "gludio_dominion", "dion_dominion", "giran_dominion", "oren_dominion",
			"aden_dominion", "innadrile_dominion", "godad_dominion", "rune_dominion", "schuttgart_dominion" };
	
	private final int _warTime;
	
	public ExReplyDominionInfo()
	{
		_warTime = (int)(System.currentTimeMillis() / 1000);
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x92);
		
		writeD(0x09); // territory count
		for (int i = 0; i < 9; i++)
		{
			writeD(0x51 + i); // territory ID
			writeS(DOM[i]); // special string
			writeS("No Clan"); // owner clan
			writeD(0x01); // emblem count
			writeD(0x51 + i); // emblem IDs (currently each ward has own emblem)
			writeD(_warTime); // next battle date
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_92_EXREPLYDOMINIONINFO;
	}
}
