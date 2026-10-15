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

import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.LoginModule;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.server.LoginFail;
import com.l2jfree.loginserver.network.packets.server.PlayOk;
import com.l2jfree.loginserver.services.exception.MaintenanceException;
import com.l2jfree.loginserver.services.exception.MaturityException;

/**
 * Fromat is ddc
 * d: first part of session id
 * d: second part of session id
 * c: server ID
 */
public class RequestServerLogin extends L2ClientPacket
{
	private int _skey1;
	private int _skey2;
	private int _serverId;
	
	/**
	 * @return
	 */
	public int getSessionKey1()
	{
		return _skey1;
	}
	
	/**
	 * @return
	 */
	public int getSessionKey2()
	{
		return _skey2;
	}
	
	/**
	 * @return
	 */
	public int getServerID()
	{
		return _serverId;
	}
	
	@Override
	protected int getMinimumLength()
	{
		return 9;
	}
	
	@Override
	public void readImpl()
	{
		_skey1 = readD();
		_skey2 = readD();
		_serverId = readC();
		/* 6 null bytes, 1 byte, 1 byte, 2 bytes, rest - null bytes
		 * 2 bytes will match with respective RequestServerList bytes, the 1 & 1 byte
		 * will both be randomly deviated.
		byte[] b = new byte[22];
		readB(b);
		_log.info("RSLog: " + HexUtil.printData(b));
		*/
		skip(22);
	}
	
	/**
	 * @see com.l2jfree.network.ReceivablePacket#run()
	 */
	@Override
	public void runImpl()
	{
		L2Client client = getClient();
		SessionKey sk = client.getSessionKey();
		
		if (LoginConfig.SECURITY_CARD_LOGIN && !client.isCardAuthed())
		{
			client.closeLoginGame(LoginFail.REASON_IGNORE);
			return;
		}
		
		// if we didn't show the license we can't check these values
		if (!LoginConfig.SHOW_LICENCE || client.hasLoginPair(_skey1, _skey2))
		{
			WorldPort world = LoginModule.currentWorld();
			boolean chosenWorldExists = world != null && world.status().serverId() == _serverId;
			if (chosenWorldExists)
			{
				// make sure the world handles the info packet
				world.expect(client.getIp());
			}
			try
			{
				// a server id that is not the id of the world is refused like a world under maintenance
				if (!chosenWorldExists)
					throw MaintenanceException.MAINTENANCE;
				
				if (LoginManager.getInstance().isLoginPossible(client.getAge(), client.getAccessLevel()))
				{
					client.setJoinedGS(true);
					client.sendPacket(new PlayOk(sk));
					LoginManager.getInstance().setAccountLastServerId(client.getAccount(), _serverId);
				}
				else
				{
					client.closeLoginGame(LoginFail.REASON_TOO_HIGH_TRAFFIC);
				}
			}
			catch (MaintenanceException e)
			{
				client.closeLoginGame(LoginFail.REASON_MAINTENANCE_UNDERGOING);
			}
			catch (MaturityException e)
			{
				client.closeLoginGame(LoginFail.REASON_AGE_LIMITATION);
			}
		}
		else
		{
			client.closeLoginGame(LoginFail.REASON_ACCESS_FAILED_TRY_AGAIN);
		}
	}
}
