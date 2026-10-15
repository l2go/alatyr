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
package com.l2jfree.gameserver.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.InetAddress;

import org.apache.commons.lang3.StringUtils;

import io.netty.channel.Channel;

import com.l2jfree.Config;
import com.l2jfree.gameserver.CoreInfo;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.FloodManager.ErrorMode;
import com.l2jfree.network.PacketHandler;
import com.l2jfree.network.NetworkConfig;
import com.l2jfree.network.NetworkServer;
import com.l2jfree.tools.util.HexUtil;

public final class L2ClientSelectorThread extends NetworkServer<L2Client, L2ClientPacket, L2ServerPacket>
{
	private static final class SingletonHolder
	{
		private static final L2ClientSelectorThread INSTANCE;
		
		static
		{
			final NetworkConfig sc = new NetworkConfig().setAcceptLimits(Config.ACCEPT_WARN, Config.ACCEPT_REJECT,
					Config.ACCEPT_SECONDS, Config.ACCEPT_WARN_LONG, Config.ACCEPT_REJECT_LONG, Config.ACCEPT_SECONDS_LONG);
			
			try
			{
				if (Config.PACKET_FINAL)
					INSTANCE = new L2ClientSelectorThread(sc, new L2ClientPacketHandlerFinal());
				else
					INSTANCE = new L2ClientSelectorThread(sc, new L2ClientPacketHandler());
			}
			catch (Exception e)
			{
				throw new Error(e);
			}
		}
	}
	
	public static L2ClientSelectorThread getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private L2ClientSelectorThread(NetworkConfig sc,
			PacketHandler<L2Client, L2ClientPacket, L2ServerPacket> packetHandler) throws IOException
	{
		super(sc, packetHandler);
	}
	
	public void printDebug(ByteBuffer buf, L2Client client, int... opcodes)
	{
		report(ErrorMode.INVALID_OPCODE, client, null, null);
		
		if (!Config.PACKET_HANDLER_DEBUG)
			return;
		
		StringBuilder sb = new StringBuilder();
		sb.append("Unknown Packet: ");
		
		for (int i = 0; i < opcodes.length; i++)
		{
			if (i != 0)
				sb.append(" : ");
			
			sb.append("0x").append(Integer.toHexString(opcodes[i]));
		}
		sb.append(", Client: ").append(client);
		_log.info(sb.toString());
		
		byte[] array = new byte[buf.remaining()];
		buf.get(array);
		for (String line : StringUtils.split(HexUtil.printData(array), "\n"))
			_log.info(line);
	}
	
	// ==============================================
	
	@Override
	protected L2Client createClient(Channel channel)
	{
		return new L2Client(this, channel);
	}
	
	@Override
	protected void executePacket(L2ClientPacket packet)
	{
		packet.getClient().execute(packet);
	}
	
	// ==============================================
	
	private final LegalConnections _legalConnections = new LegalConnections();
	
	@Override
	protected String getVersionInfo()
	{
		return CoreInfo.getVersionInfo();
	}
	
	@Override
	protected boolean acceptConnectionFrom(InetAddress address)
	{
		if (!super.acceptConnectionFrom(address))
			return false;
		
		if (!Config.CONNECTION_FILTERING)
			return true;
		
		final String ip = address.getHostAddress();
		
		return _legalConnections.consume(ip);
	}
	
	public void legalize(String ip)
	{
		_legalConnections.legalize(ip);
	}
	
	@Override
	public boolean canReceivePacketFrom(L2Client client, int opcode)
	{
		if (!super.canReceivePacketFrom(client, opcode))
			return false;
		
		if (client.isDisconnected())
			return false;
		
		return true;
	}
}
