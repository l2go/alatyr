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

import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.L2Client.LoginClientState;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.server.GGAuth;
import com.l2jfree.loginserver.network.packets.server.LoginFail;

/**
 * Considering the fact that GG packets were never understood, there's
 * no reason to hope they will be.
 */
public class AuthGameGuard extends L2ClientPacket
{
	private int _sessionId;
	
	public int getSessionId()
	{
		return _sessionId;
	}
	
	@Override
	protected int getMinimumLength()
	{
		return 20;
	}
	
	/**
	 * @see com.l2jfree.loginserver.network.packets.L2ClientPacket#readImpl()
	 */
	@Override
	protected void readImpl()
	{
		_sessionId = readD();
		
		/* 19 null bytes, 1 byte, 3 bytes, rest - null bytes
		 * 3 bytes will match with RequestAuthLogin first three bytes
		 * and the randomly deviated 1 byte will be as 4th byte in RAL
		byte[] b = new byte[35];
		readB(b);
		_log.info("AGG: " + HexUtil.printData(b));
		*/
		skip(35);
	}
	
	/**
	 * @see com.l2jfree.network.ReceivablePacket#run()
	 */
	@Override
	public void runImpl()
	{
		L2Client client = getClient();
		if (_sessionId == client.getSessionId())
		{
			client.setState(LoginClientState.AUTHED_GG);
			client.sendPacket(new GGAuth(client.getSessionId()));
		}
		else
		{
			//this.getClient().closeLogin(LoginFail.REASON_ACCESS_FAILED_TRY_AGAIN);
			client.closeLogin(LoginFail.REASON_IGNORE);
		}
	}
}
