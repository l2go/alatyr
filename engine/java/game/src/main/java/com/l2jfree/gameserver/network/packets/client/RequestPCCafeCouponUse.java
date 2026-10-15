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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format: (ch) S
 * @author  -Wooden-
 * TODO: GodKratos: This packet is wrong in Gracia Final!!
 */
public class RequestPCCafeCouponUse extends L2ClientPacket
{
	private static final String _C__D0_20_REQUESTPCCAFECOUPONUSE = "[C] D0:?? RequestPCCafeCouponUse";
	
	private String _str;
	
	/**
	 * @param buf
	 * @param client
	 */
	@Override
	protected void readImpl()
	{
		_str = readS();
	}
	
	@Override
	protected void runImpl()
	{
		//TODO: implementation missing
		_log.debug("C5: RequestPCCafeCouponUse: S: " + _str);
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__D0_20_REQUESTPCCAFECOUPONUSE;
	}
}
