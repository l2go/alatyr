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
package com.l2jfree.gameserver.extension;

import java.nio.file.Path;

import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

/**
 * What an extension may use from the running world. It does not hand out a login session key.
 */
public interface GameServerContext
{
	NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> getNetworkServer();
	
	/** The directory the server already reads its configuration from. */
	Path getConfigurationDirectory();
	
	LoginLink getLoginLink();
}
