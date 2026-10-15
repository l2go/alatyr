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
 * Format: (ch)ddddd
 * 
 */
public class ExPutIntensiveResultForVariationMake extends L2ServerPacket
{
	private static final String S_FE_54_EXPUTINTENSIVERESULTFORVARIATIONMAKE =
			"[S] FE:54 ExPutIntensiveResultForVariationMake";
	
	private final int _refinerItemObjId;
	private final int _lifestoneItemId;
	private final int _gemstoneItemId;
	private final int _gemstoneCount;
	private final int _unk2;
	
	public ExPutIntensiveResultForVariationMake(int refinerItemObjId, int lifeStoneId, int gemstoneItemId,
			int gemstoneCount)
	{
		_refinerItemObjId = refinerItemObjId;
		_lifestoneItemId = lifeStoneId;
		_gemstoneItemId = gemstoneItemId;
		_gemstoneCount = gemstoneCount;
		_unk2 = 1;
	}
	
	/**
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x54);
		writeD(_refinerItemObjId);
		writeD(_lifestoneItemId);
		writeD(_gemstoneItemId);
		writeCompQ(_gemstoneCount);
		writeD(_unk2);
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_FE_54_EXPUTINTENSIVERESULTFORVARIATIONMAKE;
	}
	
}
