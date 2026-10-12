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
package com.l2jfree.gameserver.elayne;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.ShutdownMode;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.DayNightSpawnManager;
import com.l2jfree.gameserver.instancemanager.Manager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.tools.random.SecureRnd;

public class RemoteAdministrationImpl extends UnicastRemoteObject implements IRemoteAdministration
{
	private final static Logger _log = LoggerFactory.getLogger(RemoteAdministrationImpl.class.getName());
	private static final long serialVersionUID = -8523099127883669758L;
	private static RemoteAdministrationImpl _instance;
	private IRemoteAdministration _obj;
	
	@SuppressWarnings("unused")
	private Registry _lReg;
	private String _pass;
	private int _port;
	
	public static RemoteAdministrationImpl getInstance()
	{
		if (_instance == null)
		{
			try
			{
				_instance = new RemoteAdministrationImpl();
			}
			catch (RemoteException e)
			{
				_log.error("RemoteAdministrationImpl: Problems ocurred while starting RMI Server.", e);
			}
		}
		return _instance;
	}
	
	public RemoteAdministrationImpl() throws RemoteException
	{
		super();
		_pass = Config.RMI_SERVER_PASSWORD;
		_port = Config.RMI_SERVER_PORT;
	}
	
	/** An empty password never matches, including the published object before a password is set. */
	private boolean passwordAccepted(String password)
	{
		return password != null && _pass != null && !_pass.isEmpty() && _pass.equals(password);
	}
	
	public void startServer()
	{
		if (Config.ALLOW_RMI_SERVER && _port != 0)
		{
			try
			{
				_lReg = LocateRegistry.createRegistry(_port);
				// The registry publishes this second instance. A password generated on the singleton
				// would never be the one remote calls are checked against.
				RemoteAdministrationImpl published = new RemoteAdministrationImpl();
				String password = published._pass;
				if (password == null || password.isEmpty())
				{
					_log.info("No password defined for RMI Server");
					password = generateRandomPassword(10);
					_log.info("A password has been automatically generated: " + password);
				}
				if (password == null || password.isEmpty())
				{
					_log.error("RMI Server was not started: the password is empty.");
					return;
				}
				published._pass = password;
				_pass = password;
				_obj = published;
				
				Naming.rebind("//localhost:" + _port + "/Elayne", published);
				_log.info("RMI Server started on port: " + _port + ", Password: " + _pass + ".");
			}
			catch (Exception e)
			{
				_log.error("RemoteAdministrationImpl error: ", e);
				e.printStackTrace();
			}
		}
		else
			_log.info("RMI Server is currently disabled.");
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#getOnlineUsersCount()
	 */
	@Override
	public int getOnlineUsersCount(String password) throws RemoteException
	{
		if (!passwordAccepted(password))
			return 0;
		return L2World.getInstance().getAllPlayersCount();
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#getPlayerInformation(java.lang.String)
	 */
	@Override
	public IRemotePlayer getPlayerInformation(String password, String playerName) throws RemoteException
	{
		if (!passwordAccepted(password))
			return null;
		L2Player player = L2World.getInstance().getPlayer(playerName);
		if (player != null)
			return new RemotePlayerImpl(player);
		return null;
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#announceToAll(java.lang.String)
	 */
	@Override
	public void announceToAll(String password, String announcement) throws RemoteException
	{
		if (!passwordAccepted(password))
			return;
		Announcements.getInstance().announceToAll(announcement);
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#abortServerRestart()
	 */
	@Override
	public void abortServerRestart(String password) throws RemoteException
	{
		if (passwordAccepted(password))
			Shutdown.abort("127.0.0.1");
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#kickPlayerFromServer(java.lang.String)
	 */
	@Override
	public int kickPlayerFromServer(String password, String playerName) throws RemoteException
	{
		if (passwordAccepted(password))
		{
			L2Player player = L2World.getInstance().getPlayer(playerName);
			if (player != null)
			{
				player.sendMessage("You are kicked out by a GM.");
				new Disconnection(player).defaultSequence(false);
				return 1;
			}
			return 2;
		}
		
		return 3;
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#reload(int)
	 */
	@Override
	public void reload(String password, int reloadProcedure) throws RemoteException
	{
		if (passwordAccepted(password))
		{
			switch (reloadProcedure)
			{
				case 1:
					MultisellTable.getInstance().reload();
					break;
				case 2:
					SkillTable.reload();
					break;
				case 3:
					NpcTable.getInstance().reloadAll();
					break;
				case 4:
					HtmCache.getInstance().reload(true);
					break;
				case 5:
					ItemTable.reload();
					break;
				case 6:
					Manager.reloadAll();
					break;
				case 7:
					break;
				case 8:
					TeleportLocationTable.getInstance().reloadAll();
					break;
				case 9:
					RaidBossSpawnManager.getInstance().cleanUp();
					DayNightSpawnManager.getInstance().cleanUp();
					L2World.getInstance().deleteVisibleNpcSpawns();
					NpcTable.getInstance().reloadAll();
					SpawnTable.getInstance().reloadAll();
					RaidBossSpawnManager.getInstance().reloadBosses();
					break;
			}
		}
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#scheduleServerRestart(int)
	 */
	@Override
	public void scheduleServerRestart(String password, int secondsUntilRestart) throws RemoteException
	{
		if (passwordAccepted(password))
			Shutdown.start("127.0.0.1", secondsUntilRestart, ShutdownMode.SHUTDOWN);
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#scheduleServerShutDown(int)
	 */
	@Override
	public void scheduleServerShutDown(String password, int secondsUntilShutDown) throws RemoteException
	{
		if (passwordAccepted(password))
			Shutdown.start("127.0.0.1", secondsUntilShutDown, ShutdownMode.RESTART);
		
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#sendMessageToGms(java.lang.String)
	 */
	@Override
	public int sendMessageToGms(String password, String message) throws RemoteException
	{
		if (!passwordAccepted(password))
			return 0;
		CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Alliance, "Message From Elayne GM Tool", message);
		GmListTable.broadcastToGMs(cs);
		return GmListTable.getAllGms(true).size();
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#sendPrivateMessage(java.lang.String, java.lang.String)
	 */
	@Override
	public int sendPrivateMessage(String password, String player, String message) throws RemoteException
	{
		if (!passwordAccepted(password))
			return 2;
		L2Player reciever = L2World.getInstance().getPlayer(player);
		CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Tell, "Elayne GM Tool MSG", message);
		if (reciever != null)
		{
			reciever.sendPacket(cs);
			return 1;
		}
		
		return 2;
	}
	
	/**
	 * @see com.l2jfree.gameserver.elayne.IRemoteAdministration#getOnlinePlayersDetails(java.lang.String)
	 */
	@Override
	public Map<String, IRemotePlayer> getOnlinePlayersDetails(String rmiPassword) throws RemoteException
	{
		if (!passwordAccepted(rmiPassword))
			return null;
		return null;
	}
	
	private String generateRandomPassword(int length)
	{
		StringBuilder password = new StringBuilder();
		String lowerChar = "qwertzuiopasdfghjklyxcvbnm";
		String upperChar = "QWERTZUIOPASDFGHJKLYXCVBNM";
		String digits = "1234567890";
		for (int i = 0; i < length; i++)
		{
			int charSet = SecureRnd.nextInt(3);
			switch (charSet)
			{
				case 0:
					password.append(lowerChar.charAt(SecureRnd.nextInt(lowerChar.length() - 1)));
					break;
				case 1:
					password.append(upperChar.charAt(SecureRnd.nextInt(upperChar.length() - 1)));
					break;
				case 2:
					password.append(digits.charAt(SecureRnd.nextInt(digits.length() - 1)));
					break;
			}
		}
		return password.toString();
	}
}
