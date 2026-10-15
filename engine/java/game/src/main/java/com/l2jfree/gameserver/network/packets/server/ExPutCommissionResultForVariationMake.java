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

import com.l2jfree.Config;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Format: (ch)ddddd
 *
 */
public class ExPutCommissionResultForVariationMake extends L2ServerPacket
{
	private static final String S_FE_55_EXPUTCOMMISSIONRESULTFORVARIATIONMAKE =
			"[S] FE:55 ExPutCommissionResultForVariationMake";
	
	private final int _gemstoneObjId;
	private final int _itemId;
	private final long _gemstoneCount;
	private final int _unk1;
	private final int _unk2;
	private final int _unk3;
	
	public ExPutCommissionResultForVariationMake(int gemstoneObjId, long count, int itemId)
	{
		_gemstoneObjId = gemstoneObjId;
		_itemId = itemId;
		_gemstoneCount = count;
		_unk1 = 1;
		_unk2 = 1;
		_unk3 = 1;
	}
	
	/**
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x55);
		writeD(_gemstoneObjId);
		writeD(_itemId);
		writeCompQ(_gemstoneCount);
		writeD(_unk1);
		writeD(_unk2);
		if (Config.PACKET_FINAL)
			writeD(_unk3);
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_FE_55_EXPUTCOMMISSIONRESULTFORVARIATIONMAKE;
	}
}
