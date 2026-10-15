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
package com.l2jfree.loginserver.network.packets.server;

import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;

/**
 * Fromat: d
 * d: response
 */
public final class GGAuth extends L2ServerPacket
{
	public static final int SKIP_GG_AUTH_REQUEST = 0x0b;
	
	private final int _response;
	
	public GGAuth(int response)
	{
		_response = response;
		if (_log.isDebugEnabled())
		{
			_log.warn("Reason Hex: " + (Integer.toHexString(response)));
		}
	}
	
	/**
	 * @see com.l2jfree.network.SendablePacket#write()
	 */
	@Override
	protected void write(L2Client client)
	{
		writeC(0x0b);
		writeD(_response);
		writeD(0x00);
		writeD(0x00);
		writeD(0x00);
		writeD(0x00);
	}
}
