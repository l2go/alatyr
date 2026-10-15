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

import com.l2jfree.gameserver.model.L2CommandChannel;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Format:(ch) sdd d[sdd]
 * @author  Crion/kombat
 */

public class ExMultiPartyCommandChannelInfo extends L2ServerPacket
{
	private final L2CommandChannel _cc;
	
	public ExMultiPartyCommandChannelInfo(L2CommandChannel cc)
	{
		_cc = cc;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x31);
		
		writeS(_cc.getChannelLeader().getName());
		writeD(0x00);
		writeD(_cc.getMemberCount());
		
		writeD(_cc.getPartys().size());
		for (L2Party party : _cc.getPartys())
		{
			writeS(party.getLeader().getName());
			writeD(party.getPartyLeaderOID());
			writeD(party.getMemberCount());
		}
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "FE_31_ExMultiPartyCommandChannelInfo";
	}
}
