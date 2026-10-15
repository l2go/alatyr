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
 *
 * @author nBd
 */
public class RequestEnchant extends L2ServerPacket
{
	private final int _unk;
	private static final String _S__FE_81_REQUESTENCHANT = "[S] FE:81 RequestEnchant";
	
	/**
	 * 
	 */
	public RequestEnchant(int value)
	{
		_unk = value;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__FE_81_REQUESTENCHANT;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x81);
		writeD(_unk);
	}
}
