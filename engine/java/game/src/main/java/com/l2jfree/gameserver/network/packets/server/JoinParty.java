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

public class JoinParty extends L2ServerPacket
{
	private static final String _S__JOINPARTY = "[S] 3A JoinParty c[d]";
	public static final JoinParty ACCEPTED = new JoinParty(0x01);
	public static final JoinParty DECLINED = new JoinParty(0x00);
	
	private final int _response;
	
	private JoinParty(int response)
	{
		_response = response;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x3a);
		writeD(_response);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__JOINPARTY;
	}
}
