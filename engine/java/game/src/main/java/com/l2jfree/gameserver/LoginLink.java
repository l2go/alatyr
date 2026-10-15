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
package com.l2jfree.gameserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.ServerStatusAttributes;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldPort;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
import com.l2jfree.gameserver.network.L2ClientSelectorThread;
import com.l2jfree.gameserver.network.WorldAddress;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;
import com.l2jfree.gameserver.network.packets.server.LoginFail;

/**
 * The link between the world and the login module of the same process. It implements {@link WorldPort}, which the
 * login module calls, and it calls the {@link LoginPort} that {@link #connect(LoginPort)} hands over.
 * <p>
 * It keeps the bookkeeping of the clients: the clients that wait for an admission and the accounts that are in
 * the world. The login module is never called while the account lock is held.
 */
public final class LoginLink implements WorldPort
{
	private static final Logger _log = LoggerFactory.getLogger(LoginLink.class);

	private static final class SingletonHolder
	{
		private static final LoginLink INSTANCE = new LoginLink();
	}

	public static LoginLink getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	/** What the world does with its clients when the login module decides. */
	private static final class GameClients implements PlaySessions.Clients<L2Client>
	{
		@Override
		public void claim(L2Client client, String account)
		{
			client.setAccountName(account);
		}

		@Override
		public void admit(final L2Client client, SessionKey key, String hostAddress)
		{
			client.setState(GameClientState.AUTHED);
			client.setSessionId(key);
			client.setHostAddress(hostAddress);

			// executing the sql query on the thread pool
			client.getPacketQueue().execute(new Runnable() {
				@Override
				public void run()
				{
					client.sendPacket(new CharSelectionInfo(client));
				}
			});
		}

		@Override
		public void refuse(L2Client client)
		{
			client.sendPacket(new LoginFail(LoginFail.SYSTEM_ERROR_LOGIN_LATER));
			client.closeNow();
		}

		@Override
		public void close(L2Client client)
		{
			client.closeNow();
		}
	}

	private volatile PlaySessions<L2Client> _sessions;
	private volatile LoginPort _login;
	private volatile WorldAddress _address;
	private volatile ServerStatus _status = ServerStatus.STATUS_AUTO;

	/** For tests: a link of its own. The server uses {@link #getInstance()}. */
	LoginLink()
	{
	}

	/**
	 * Hands the login module to the world. It must be called once, before the world port opens.
	 *
	 * @param login the port of the login module
	 */
	public void connect(LoginPort login)
	{
		if (login == null)
			throw new IllegalArgumentException("login");

		synchronized (this)
		{
			if (_login != null)
				throw new IllegalStateException("The world is already connected to a login module");

			_address = new WorldAddress(WorldAddress.netConfig(Config.EXTERNAL_HOSTNAME, Config.INTERNAL_HOSTNAME,
					Config.SUBNETWORKS),
					Config.IP_UPDATE_TIME);
			_status = Config.SERVER_GMONLY ? ServerStatus.STATUS_GM_ONLY : ServerStatus.STATUS_AUTO;
			_login = login;
			_sessions = new PlaySessions<L2Client>(login, new GameClients());
		}

		_log.info("Connected to the login module, world status is {}", _status);
	}

	private LoginPort login()
	{
		LoginPort login = _login;
		if (login == null)
			throw new IllegalStateException("The world is not connected to a login module");

		return login;
	}

	private PlaySessions<L2Client> sessions()
	{
		PlaySessions<L2Client> sessions = _sessions;
		if (sessions == null)
			throw new IllegalStateException("The world is not connected to a login module");

		return sessions;
	}

	// ---------------------------------------------------------------------------------------------
	// calls from the world to the login module
	// ---------------------------------------------------------------------------------------------

	/**
	 * Claims the account for the client and asks the login module to admit it. The answer is applied before the
	 * method returns.
	 *
	 * @return false when the account is already waiting or in the world, true when the claim was accepted
	 */
	public boolean addWaitingClientAndSendRequest(String acc, L2Client client, SessionKey key)
	{
		return sessions().authenticate(acc, client, key);
	}

	public void sendLogout(String account, L2Client client)
	{
		sessions().logout(account, client);
	}

	public void sendAccessLevel(String account, int level)
	{
		final LoginPort login = login();
		try
		{
			login.changeAccessLevel(account, level);
		}
		catch (RuntimeException e)
		{
			_log.warn("Access level of " + account + " could not be changed", e);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// WorldPort: calls from the login module to the world
	// ---------------------------------------------------------------------------------------------

	@Override
	public WorldStatus status()
	{
		return new WorldStatus(Config.SERVER_ID, _status, Config.PORT_GAME, sessions().inWorldCount(),
				Config.MAXIMUM_ONLINE_USERS, Config.SERVER_AGE_LIM, Config.SERVER_PVP, Config.SERVER_LIST_CLOCK,
				Config.SERVER_LIST_BRACKET, Config.SERVER_LIST_TESTSERVER, Config.SERVER_BIT_3, Config.SERVER_BIT_1);
	}

	@Override
	public String addressFor(String clientIp)
	{
		WorldAddress address = _address;
		if (address == null)
			throw new IllegalStateException("The world is not connected to a login module");

		return address.addressFor(clientIp);
	}

	@Override
	public void kick(String account)
	{
		sessions().kick(account);

		L2Player.disconnectIfOnline(account);
	}

	@Override
	public void expect(String clientIp)
	{
		L2ClientSelectorThread.getInstance().legalize(clientIp);
	}

	// ---------------------------------------------------------------------------------------------
	// server status
	// ---------------------------------------------------------------------------------------------

	public ServerStatus getServerStatus()
	{
		return _status;
	}

	public void setServerStatus(int status)
	{
		changeAttribute(ServerStatusAttributes.SERVER_LIST_STATUS, status);
	}

	public void setServerStatusDown()
	{
		setServerStatus(ServerStatus.STATUS_DOWN.ordinal());
	}

	public int getMaxPlayer()
	{
		return Config.MAXIMUM_ONLINE_USERS;
	}

	public void setMaxPlayers(int maxPlayer)
	{
		changeAttribute(ServerStatusAttributes.SERVER_LIST_MAX_PLAYERS, maxPlayer);
	}

	public void changeAttribute(int attr, int value)
	{
		changeAttribute(ServerStatusAttributes.valueOf(attr), value);
	}

	/**
	 * Changes an attribute of the server list entry. The login module reads the result through {@link #status()}.
	 */
	public void changeAttribute(ServerStatusAttributes attr, int value)
	{
		switch (attr)
		{
			case SERVER_LIST_STATUS:
				_status = ServerStatus.valueOf(value);
				switch (_status)
				{
					case STATUS_DOWN:
						if (!Shutdown.isInProgress())
							kickPlayers();
						break;
					default:
						break;
				}
				break;
			case SERVER_LIST_UNK:
				Config.SERVER_BIT_1 = (value > 0);
				break;
			case SERVER_LIST_CLOCK:
				Config.SERVER_LIST_CLOCK = (value > 0);
				break;
			case SERVER_LIST_HIDE_NAME:
				Config.SERVER_BIT_3 = (value > 0);
				break;
			case TEST_SERVER:
				Config.SERVER_LIST_TESTSERVER = (value > 0);
				break;
			case SERVER_LIST_BRACKETS:
				Config.SERVER_LIST_BRACKET = (value > 0);
				break;
			case SERVER_LIST_MAX_PLAYERS:
				Config.MAXIMUM_ONLINE_USERS = value;
				break;
			case SERVER_LIST_PVP:
				Config.SERVER_PVP = (value > 0);
				break;
			case SERVER_AGE_LIMITATION:
				Config.SERVER_AGE_LIM = value;
				break;
			case NONE:
			default:
				return;
		}
	}

	private void kickPlayers()
	{
		int counter = 0;
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (player.isGM())
				continue;

			try
			{
				new Disconnection(player).defaultSequence(true);
				counter++;
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
		}

		_log.info(counter + " players were auto-kicked.");
	}
}
