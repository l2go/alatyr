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
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.KeyPacket;

/**
 * This class represents the first packet that is sent by the client to the game server.
 */
public class ProtocolVersion extends L2ClientPacket
{
	private static final String _C__PROTOCOLVERSION = "[C] 0E ProtocolVersion c[unk] (changes often)";
	
	private int _version;
	
	@Override
	protected void readImpl()
	{
		_version = readD();
		/* A block of bytes
		byte[] b = new byte[260];
		readB(b);
		_log.info(HexUtil.printData(b));
		*/
		if (Config.STRICT_FINAL)
			skip(260);
		else
			skipAll();
	}
	
	@Override
	protected void runImpl()
	{
		L2Client client = getClient();
		KeyPacket kp;
		// this packet is never encrypted
		if (_version == -2)
		{
			if (_log.isDebugEnabled())
				_log.info("Ping received");
			// this is just a ping attempt from the C2+ client
			client.closeNow();
		}
		else if (_version < Config.MIN_PROTOCOL_REVISION)
		{
			_log.info("Client Protocol Revision:" + _version + " is too low. only " + Config.MIN_PROTOCOL_REVISION
					+ " and " + Config.MAX_PROTOCOL_REVISION + " are supported. Closing connection.");
			_log.warn("Wrong Protocol Version " + _version);
			kp = new KeyPacket(client.enableCrypt(), 0);
			client.sendPacket(kp);
			client.setProtocolOk(false);
		}
		else if (_version > Config.MAX_PROTOCOL_REVISION)
		{
			_log.info("Client Protocol Revision:" + _version + " is too high. only " + Config.MIN_PROTOCOL_REVISION
					+ " and " + Config.MAX_PROTOCOL_REVISION + " are supported. Closing connection.");
			_log.warn("Wrong Protocol Version " + _version);
			kp = new KeyPacket(client.enableCrypt(), 0);
			client.sendPacket(kp);
			client.setProtocolOk(false);
		}
		else
		{
			if (_log.isDebugEnabled())
				_log.debug("Client Protocol Revision is ok: " + _version);
			kp = new KeyPacket(client.enableCrypt(), 1);
			sendPacket(kp);
			client.setProtocolOk(true);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__PROTOCOLVERSION;
	}
}
