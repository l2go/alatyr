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

import com.l2jfree.Config;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.LoginFail;

/**
 * This class represents the packet that is sent by the client when the server
 * selection is confirmed.
 * 
 * Note for sync: packet structure changed in Epilogue.
 */
public class AuthLogin extends L2ClientPacket
{
	private static final String _C__AUTHLOGIN = "[C] 2B AuthLogin c[sdddddddd] (unk) (changes often)";
	
	// loginName + keys must match what the login server used.
	private String _loginName;
	private int _playKey1;
	private int _playKey2;
	private int _loginKey1;
	private int _loginKey2;
	
	@Override
	protected void readImpl()
	{
		_loginName = readS().toLowerCase();
		_playKey2 = readD();
		_playKey1 = readD();
		_loginKey1 = readD();
		_loginKey2 = readD();
		if (Config.STRICT_FINAL)
			skip(16);
		else
			skipAll();
	}
	
	@Override
	protected void runImpl()
	{
		if (!getClient().isProtocolOk())
			return;
		
		SessionKey key = new SessionKey(_loginKey1, _loginKey2, _playKey1, _playKey2);
		if (_log.isDebugEnabled())
		{
			_log.info("User: " + _loginName);
			_log.info("Key: " + key);
		}
		
		L2Client client = getClient();
		// avoid potential exploits
		if (client.getAccountName() == null)
		{
			if (!LoginLink.getInstance().addWaitingClientAndSendRequest(_loginName, client, key))
			{
				client.sendPacket(new LoginFail(LoginFail.ACOUNT_ALREADY_IN_USE));
				client.closeNow();
				return;
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _C__AUTHLOGIN;
	}
}
