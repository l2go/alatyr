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
package com.l2jfree.loginserver.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.InetAddress;

import org.apache.commons.lang3.StringUtils;

import io.netty.channel.Channel;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.manager.BanManager;
import com.l2jfree.loginserver.manager.LoginManager;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;
import com.l2jfree.loginserver.network.packets.server.Init;
import com.l2jfree.network.FloodManager.ErrorMode;
import com.l2jfree.network.PacketHandler;
import com.l2jfree.network.NetworkConfig;
import com.l2jfree.network.NetworkServer;
import com.l2jfree.tools.util.HexUtil;
import com.l2jfree.util.concurrent.ExecuteWrapper;
import com.l2jfree.util.concurrent.VirtualTaskExecutor;

public final class L2ClientSelectorThread extends
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket>
{
	private static final class SingletonHolder
	{
		private static final L2ClientSelectorThread INSTANCE;
		
		static
		{
			final NetworkConfig sc = new NetworkConfig().setAcceptLimits(LoginConfig.ACCEPT_WARN,
					LoginConfig.ACCEPT_REJECT, LoginConfig.ACCEPT_SECONDS, LoginConfig.ACCEPT_WARN_LONG,
					LoginConfig.ACCEPT_REJECT_LONG, LoginConfig.ACCEPT_SECONDS_LONG);
			
			try
			{
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
	
	public void printDebug(ByteBuffer buf, L2Client client, int opcode)
	{
		report(ErrorMode.INVALID_OPCODE, client, null, null);
		
		//if (!Config.PACKET_HANDLER_DEBUG)
		//	return;
		
		StringBuilder sb = new StringBuilder("Unknown Packet: ");
		sb.append("0x").append(Integer.toHexString(opcode));
		sb.append(", Client: ").append(client);
		_log.info(sb);
		
		byte[] array = new byte[buf.remaining()];
		buf.get(array);
		for (String line : StringUtils.split(HexUtil.printData(array), "\n"))
			_log.info(line);
	}
	
	// ==============================================
	
	@Override
	protected L2Client createClient(Channel channel)
	{
		L2Client client = new L2Client(this, channel);
		client.sendPacket(new Init(client));
		return client;
	}
	
	/** Login packets wait on the database, so each runs on its own virtual thread. */
	private final VirtualTaskExecutor _generalPacketsThreadPool = new VirtualTaskExecutor("login-packet");
	
	@Override
	protected void executePacket(L2ClientPacket packet)
	{
		// the packets of one client run in order; those of different clients run side by side
		packet.getClient().executeInOrder(new ExecuteWrapper(packet), _generalPacketsThreadPool);
	}
	
	// ==============================================
	
	@Override
	protected boolean acceptConnectionFrom(InetAddress address)
	{
		if (!super.acceptConnectionFrom(address))
			return false;
		
		// Ignore permabanned IPs
		if (BanManager.getInstance().isRestrictedAddress(address))
			return false;
		
		return true;
	}
}
