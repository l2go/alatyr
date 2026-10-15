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
 * This class represents a packet sent to the client when it fails to login to a GameServer.
 * @version $Revision: 1.2.4.1 $ $Date: 2005/03/27 15:30:11 $
 */
public final class PlayFail extends L2ServerPacket
{
	private final int _reason;
	
	/**
	 * @param reason Taken from LoginFail (the messages are always the same)
	 */
	public PlayFail(int reason)
	{
		_reason = reason;
	}
	
	/**
	 * @see com.l2jfree.network.SendablePacket#write()
	 */
	@Override
	protected void write(L2Client client)
	{
		writeC(0x06);
		writeC(_reason);
	}
}
