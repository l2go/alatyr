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
public class ExPutEnchantSupportItemResult extends L2ServerPacket
{
	private static final String _S__82_EXPUTENCHANTSUPPORTITEMRESULT = "[S] 82 ExPutEnchantSupportItemResult";
	
	private final int _result;
	
	/**
	 * 
	 */
	public ExPutEnchantSupportItemResult(int result)
	{
		_result = result;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__82_EXPUTENCHANTSUPPORTITEMRESULT;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x82);
		writeD(_result);
	}
}
