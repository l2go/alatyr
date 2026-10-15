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

import java.nio.ByteBuffer;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.loginserver.network.L2Client.LoginClientState;
import com.l2jfree.loginserver.network.packets.L2ClientPacket;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;
import com.l2jfree.loginserver.network.packets.client.AuthGameGuard;
import com.l2jfree.loginserver.network.packets.client.RequestAuthLogin;
import com.l2jfree.loginserver.network.packets.client.RequestServerList;
import com.l2jfree.loginserver.network.packets.client.RequestServerLogin;
import com.l2jfree.loginserver.network.packets.client.RequestSubmitCardNo;
import com.l2jfree.network.PacketHandler;

/**
 * Handler for packets received by Login Server
 * 
 * @author KenM
 */
public final class L2ClientPacketHandler implements
		PacketHandler<L2Client, L2ClientPacket, L2ServerPacket>
{
	@Override
	public L2ClientPacket handlePacket(ByteBuffer buf, L2Client client, final int opcode)
	{
		final LoginClientState state = client.getState();
		
		switch (state)
		{
			case CONNECTED:
				if (opcode == 0x07)
				{
					return new AuthGameGuard();
				}
				else
				{
					debugOpcode(buf, client, opcode);
				}
				break;
			case AUTHED_GG:
				if (opcode == 0x00)
				{
					return new RequestAuthLogin();
				}
				else
				{
					debugOpcode(buf, client, opcode);
				}
				break;
			case AUTHED_LOGIN:
				if (opcode == 0x05)
				{
					return new RequestServerList();
				}
				else if (opcode == 0x02)
				{
					return new RequestServerLogin();
				}
				else if (opcode == 0x06)
				{
					if (LoginConfig.SECURITY_CARD_LOGIN)
						return new RequestSubmitCardNo();
				}
				else
				{
					debugOpcode(buf, client, opcode);
				}
				break;
		}
		
		return null;
	}
	
	private void debugOpcode(ByteBuffer buf, L2Client client, int opcode)
	{
		L2ClientSelectorThread.getInstance().printDebug(buf, client, opcode);
	}
}
