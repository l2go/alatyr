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

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.l2jfree.contract.WorldPort;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.loginserver.LoginModule;
import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;

/**
 * ServerList<BR>
 * Format: <B>cc <I>[cddcchhcdc]</I></B>
 * <BR><BR>
 * <B>c: server list size (number of servers)</B><BR>
 * <B>c: last server successfully selected by client</B><BR>
 * <B><I>[ (repeats for each server)</I></B><BR>
 * <B><I>c</I></B>: server ID <I>(The number to the left from the server name)</I><BR>
 * <B><I>d</I></B>: server IP<BR>
 * <B><I>d</I></B>: server port<BR>
 * <B><I>c</I></B>: age limit <I>(a box with this number under server Type)</I><BR>
 * <B><I>c</I></B>: PvP or not <I>(NOT DISPLAYED BY NA/EU Client)</I><BR>
 * <B><I>h</I></B>: current number of players <I>(status is calculated from this)</I><BR>
 * <B><I>h</I></B>: max number of players <I>(status is calculated from this)</I><BR>
 * <B><I>c</I></B>: 0 if server is down<BR>
 * <B><I>d</I></B>: 2nd bit: clock <BR>
 *    3rd bit: wont display server name <I>(NO EFFECT BY NA/EU Client)</I><BR>
 *    4th bit: test server <I>(if true, only test clients will list the server)</I><BR>
 * <B><I>c</I></B>: 0 if you don't want to display brackets in front of sever name<BR>
 * <B><I>]</I></B>
 * <BR><BR>
 * The platform has one world, so the list has one entry, or none while the login module has no world.
 * <BR><BR>
 * Server will be considered as "Good" when the number of online players
 * is less than half the maximum; as "Normal" between half and 4/5
 * and "Full" when there's more than 4/5 of the maximum number of players
 * @author unknown
 * @reworked by savormix
 */
public final class ServerList extends L2ServerPacket
{
	/** The only world of the platform, or null when the login module has no world. */
	private final ServerData _server;
	
	private static final class ServerData
	{
		protected final int _serverId;
		protected final String _ip;
		protected final int _port;
		protected final int _ageLim;
		protected final boolean _pvp;
		protected final int _currentPlayers;
		protected final int _maxPlayers;
		protected final boolean _online;
		protected final boolean _unk1;
		protected final boolean _clock;
		protected final boolean _hideName;
		protected final boolean _testServer;
		protected final boolean _brackets;
		
		private ServerData(int pServer_id, String pIp, int pPort, int pAge, boolean pPvp, int pCurrentPlayers,
				int pMaxPlayers, boolean pOn, boolean pUnk1, boolean pClock, boolean pHideName, boolean pTestServer,
				boolean pBrackets)
		{
			_serverId = pServer_id;
			_ip = pIp;
			_port = pPort;
			_ageLim = pAge;
			_pvp = pPvp;
			_currentPlayers = pCurrentPlayers;
			_maxPlayers = pMaxPlayers;
			_online = pOn;
			_unk1 = pUnk1;
			_clock = pClock;
			_hideName = pHideName;
			_testServer = pTestServer;
			_brackets = pBrackets;
		}
	}
	
	public ServerList(L2Client client)
	{
		this(LoginModule.currentWorld(), client);
	}
	
	/** @param world the world of the platform, or null while the login module has none */
	ServerList(WorldPort world, L2Client client)
	{
		if (world == null)
		{
			_server = null;
			return;
		}
		
		WorldStatus status = world.status();
		_server = new ServerData(status.serverId(), world.addressFor(client.getIp()), status.port(),
				status.ageLimit(), status.pvp(), status.onlinePlayers(), status.maxPlayers(), status.online(),
				status.unknownBit(), status.clock(), status.hideName(), status.testServer(), status.brackets());
	}
	
	@Override
	public void write(L2Client client)
	{
		writeC(0x04);
		writeC(_server == null ? 0 : 1);
		
		if (_server != null && _server._online && _server._serverId == client.getLastServerId())
			writeC(_server._serverId);
		else
			writeC(0);
		
		if (_server != null)
		{
			ServerData server = _server;
			
			writeC(server._serverId);
			
			try
			{
				InetAddress i4 = InetAddress.getByName(server._ip);
				byte[] raw = i4.getAddress();
				writeC(raw[0] & 0xff);
				writeC(raw[1] & 0xff);
				writeC(raw[2] & 0xff);
				writeC(raw[3] & 0xff);
			}
			catch (UnknownHostException e)
			{
				e.printStackTrace();
				writeC(127);
				writeC(0);
				writeC(0);
				writeC(1);
			}
			
			writeD(server._port);
			writeC(server._ageLim); // age limit
			writeC(server._pvp ? 0x01 : 0x00);
			writeH(server._currentPlayers);
			writeH(server._maxPlayers);
			writeC(server._online ? 0x01 : 0x00);
			
			int bits = 0;
			if (server._unk1)
				bits |= 0x01;
			if (server._clock)
				bits |= 0x02;
			if (server._hideName)
				bits |= 0x03;
			if (server._testServer)
				bits |= 0x04;
			writeD(bits);
			
			writeC(server._brackets ? 0x01 : 0x00);
		}
	}
}
