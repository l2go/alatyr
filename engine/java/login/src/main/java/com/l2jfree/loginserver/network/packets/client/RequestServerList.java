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
package com.l2jfree.loginserver.network.packets.client;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.server.LoginFail;
import com.l2jfree.loginserver.network.packets.server.ServerList;

/**
 * Format: ddc
 * d: fist part of session id
 * d: second part of session id
 * c: ?
 */
public class RequestServerList extends L2ClientPacket
{
	private int _skey1;
	private int _skey2;
	
	public int getSessionKey1()
	{
		return _skey1;
	}
	
	public int getSessionKey2()
	{
		return _skey2;
	}
	
	@Override
	protected int getMinimumLength()
	{
		return 8;
	}
	
	@Override
	public void readImpl()
	{
		_skey1 = readD(); // loginOk 1
		_skey2 = readD(); // loginOk 2
		
		// the byte equal to 4 must be related to _serverId in RSLog
		/* A byte equal to 4, 6 null bytes, 1 byte, 1 byte, 2 bytes, rest - null bytes
		 * 2 bytes will match with respective RequestServerLogin bytes, the 1 & 1 byte
		 * will both be randomly deviated. Also, since RSLog doesn't start with byte=4,
		 * there is a shift by one byte to the left.
		byte[] b = new byte[23];
		readB(b);
		_log.info("RSLi: " + HexUtil.printData(b));
		*/
		skip(23);
	}
	
	/**
	 * @see com.l2jfree.network.ReceivablePacket#run()
	 */
	@Override
	public void runImpl()
	{
		L2Client client = getClient();
		if (LoginConfig.SECURITY_CARD_LOGIN && !client.isCardAuthed())
		{
			client.closeLogin(LoginFail.REASON_IGNORE);
			return;
		}
		
		if (client.hasLoginPair(_skey1, _skey2))
			client.sendPacket(new ServerList(client));
		else
			client.closeLogin(LoginFail.REASON_ACCESS_FAILED_TRY_AGAIN);
	}
}
