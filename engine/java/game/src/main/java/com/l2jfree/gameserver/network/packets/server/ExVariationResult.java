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
 * Format: (ch)ddd
 * 
 */
public class ExVariationResult extends L2ServerPacket
{
	private static final String _S__FE_55_EXVARIATIONRESULT = "[S] FE:55 ExVariationResult";
	
	private final int _stat12;
	private final int _stat34;
	private final int _unk3;
	
	public ExVariationResult(int unk1, int unk2, int unk3)
	{
		_stat12 = unk1;
		_stat34 = unk2;
		_unk3 = unk3;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x56);
		writeD(_stat12);
		writeD(_stat34);
		writeD(_unk3);
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_55_EXVARIATIONRESULT;
	}
	
}
