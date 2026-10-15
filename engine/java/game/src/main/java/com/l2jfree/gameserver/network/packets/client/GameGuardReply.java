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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class GameGuardReply extends L2ClientPacket
{
	private static final String _C__GAMEGUARDREPLY = "[C] CA GameGuardReply c[dddd]";
	
	private static final byte[] VALID = { 0xFFFFFFF6, 0x59, 0xFFFFFFDE, 0xFFFFFFE4, 0x0, 0xFFFFFFD5, 0x3, 0xFFFFFF82,
			0xFFFFFFEA, 0xFFFFFFAC, 0xFFFFFFB5, 0xFFFFFF95, 0x0, 0x1A, 0xFFFFFFE7, 0xFFFFFFB6, 0x10, 0xFFFFFFE3,
			0xFFFFFF84, 0xFFFFFFB3 };
	
	private final byte[] _reply = new byte[8];
	
	@Override
	protected void readImpl()
	{
		readB(_reply, 0, 4);
		readD();
		readB(_reply, 4, 4);
		readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Client client = getClient();
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA");
			byte[] result = md.digest(_reply);
			if (Arrays.equals(result, VALID))
				client.setGameGuardOk(true);
		}
		catch (NoSuchAlgorithmException e)
		{
			_log.warn("Strange, the server should have already died?!", e);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__GAMEGUARDREPLY;
	}
}
