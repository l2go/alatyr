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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author Kerberos
 */
public class ExBrExtraUserInfo extends L2ServerPacket
{
	private final int _charObjId;
	private final int _val;
	
	protected ExBrExtraUserInfo(L2Player player)
	{
		_charObjId = player.getObjectId();
		_val = player.getAfroHaircutId();
	}
	
	/**
	 * This packet should belong to Quest windows, not UserInfo in T3.
	 */
	@Override
	protected final void writeImpl()
	{
		writeC(0xfe);
		if (Config.PACKET_FINAL)
			writeH(0xac);
		else
			writeH(0x8d);
		writeD(_charObjId); // object id of player
		writeD(_val); // afro hair cut
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "[S] FE:8D ExBrExtraUSerInfo";
	}
}
