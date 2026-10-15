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

import com.l2jfree.contract.SessionKey;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;

/**
 * Format: dddddddd
 * f: the session key
 * d: ?
 * d: ?
 * d: ?
 * d: ?
 * d: ?
 * d: ?
 * b: 16 bytes - unknown
 */
public final class LoginOk extends L2ServerPacket
{
	private final int _loginOk1, _loginOk2;
	
	public LoginOk(SessionKey sessionKey)
	{
		_loginOk1 = sessionKey.loginOk1();
		_loginOk2 = sessionKey.loginOk2();
	}
	
	/**
	 * @see com.l2jfree.network.SendablePacket#write()
	 */
	@Override
	protected void write(L2Client client)
	{
		writeC(0x03);
		writeD(_loginOk1);
		writeD(_loginOk2);
		writeD(0x00);
		writeD(0x00);
		writeD(0x000003ea);
		writeD(0x00);
		writeD(0x00);
		writeD(0x00);
		writeB(new byte[16]);
	}
}
