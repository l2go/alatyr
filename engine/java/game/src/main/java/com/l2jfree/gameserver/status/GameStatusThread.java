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
package com.l2jfree.gameserver.status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.config.L2Properties;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.ShutdownMode;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.Manager;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.model.GMAudit;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.TradeList.TradeItem;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.zone.L2JailZone;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.taskmanager.LeakTaskManager;
import com.l2jfree.gameserver.util.DynamicExtension;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.L2Thread;
import com.l2jfree.util.concurrent.RunnableStatsManager;
import com.l2jfree.util.concurrent.RunnableStatsManager.SortBy;
import com.l2jfree.util.logging.ListeningLog;
import com.l2jfree.util.logging.ListeningLog.LogListener;

public final class GameStatusThread extends Thread
{
	private static final Logger _log = LoggerFactory.getLogger(GameStatusThread.class);
	
	private String _gm;
	
	private final Socket _cSocket;
	
	private final PrintWriter _print;
	private final BufferedReader _read;
	
	private final long _uptime;
	
	private void telnetOutput(int type, String text)
	{
		if (Config.DEVELOPER)
		{
			if (type == 1)
				_log.info("TELNET | " + text);
			else if (type == 2)
				_log.info("TELNET | " + text);
			else if (type == 3)
				_log.info(text);
			else if (type == 4)
				_log.info(text);
			else
				_log.info("TELNET | " + text);
		}
		else
		{
			// only print output if the message is rejected
			if (type == 5)
				_log.info("TELNET | " + text);
		}
	}
	
	private boolean isValidIP(Socket client)
	{
		boolean result = false;
		
		String clientStringIP = client.getInetAddress().getHostAddress();
		
		telnetOutput(1, "Connection from: " + clientStringIP);
		
		// read and loop thru list of IPs, compare with newIP
		if (Config.DEVELOPER)
			telnetOutput(2, "");
		
		try
		{
			L2Properties telnetSettings = new L2Properties(L2AutoInitialization.TELNET_FILE);
			
			String HostList = telnetSettings.getProperty("ListOfHosts", "127.0.0.1,localhost");
			
			if (Config.DEVELOPER)
				telnetOutput(3, "Comparing ip to list...");
			
			// compare
			String ipToCompare = null;
			for (String ip : HostList.split(","))
			{
				if (!result)
				{
					ipToCompare = InetAddress.getByName(ip).getHostAddress();
					if (clientStringIP.equals(ipToCompare))
						result = true;
					if (Config.DEVELOPER)
						telnetOutput(3, clientStringIP + " = " + ipToCompare + "(" + ip + ") = " + result);
				}
			}
		}
		catch (IOException e)
		{
			if (Config.DEVELOPER)
				telnetOutput(4, "");
			telnetOutput(1, "Error: " + e);
		}
		
		if (Config.DEVELOPER)
			telnetOutput(4, "Allow IP: " + result);
		return result;
	}
	
	public GameStatusThread(Socket client, long uptime, String StatusPW) throws IOException
	{
		_cSocket = client;
		_uptime = uptime;
		
		_print = new PrintWriter(_cSocket.getOutputStream());
		_read = new BufferedReader(new InputStreamReader(_cSocket.getInputStream()));
		
		if (!isValidIP(client))
		{
			telnetOutput(5, "Connection attempt from " + client.getInetAddress().getHostAddress() + " rejected.");
			_cSocket.close();
			return;
		}
		
		telnetOutput(1, client.getInetAddress().getHostAddress() + " accepted!");
		_print.println("Welcome to the l2core Telnet Server...");
		_print.println("Please insert your Password!");
		_print.print("Password: ");
		_print.flush();
		String tmpLine = readLine();
		if (tmpLine == null)
		{
			_print.println("Error during Connection!");
			_print.println("Disconnected...");
			_print.flush();
			_cSocket.close();
			return;
		}
		
		if (tmpLine.compareTo(StatusPW) != 0)
		{
			_print.println("Incorrect Password!");
			_print.println("Disconnected...");
			_print.flush();
			_cSocket.close();
			return;
		}
		
		if (Config.ALT_TELNET)
		{
			_print.println("Password Correct!");
			_print.print("GM name: ");
			_print.flush();
			_gm = readLine();
			
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement stmt =
						con.prepareStatement("SELECT access_level FROM player WHERE name = ?");
				stmt.setString(1, _gm);
				ResultSet rs = stmt.executeQuery();
				// a name that is not a character gives no row, and a character below the GM level is no GM
				if (!rs.next() || rs.getInt(1) < 100)
				{
					_print.println("No GMs of that name, disconnected...");
					_print.flush();
					_cSocket.close();
					return;
				}
				else
				{
					_print.println("Welcome, " + _gm);
				}
				
				rs.close();
				stmt.close();
			}
			catch (Exception e)
			{
				_print.println("Error, disconnected...");
				_print.flush();
				_cSocket.close();
				return;
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			telnetOutput(4, _gm + " successfully connected to Telnet.");
		}
		else
		{
			_print.println("Connection accepted... Welcome!");
		}
		
		_print.println("[l2core telnet console]");
		_print.print("");
		_print.flush();
		start();
	}
	
	/*
	 * Handling backspaces.
	 */
	private String readLine() throws IOException
	{
		String line = _read.readLine();
		if (line == null)
			return null;
		
		StringBuilder sb = new StringBuilder(line);
		
		for (int index; (index = sb.indexOf("\b")) != -1;)
			sb.replace(Math.max(0, index - 1), index + 1, "");
		
		return sb.toString();
	}
	
	@Override
	public void run()
	{
		String _usrCommand = "";
		try
		{
			while (_usrCommand.compareTo("quit") != 0 && _usrCommand.compareTo("exit") != 0)
			{
				_usrCommand = readLine();
				if (_usrCommand == null)
				{
					_cSocket.close();
					break;
				}
				if (_usrCommand.equals("help"))
				{
					_print.println("The following is a list of all available commands: ");
					_print.println("help				- shows this help.");
					_print.println("status				- displays basic server statistics.");
					_print.println("printmemusage		- displays memory amounts in JVM.");
					_print.println("performance			- shows server performance statistics.");
					_print.println("threads				- dumps thread infos.");
					_print.println("purge				- purges TPM.");
					_print.println("gc					- forced garbage collection.");
					_print.println("clean				- cleans leakmanager mapped objects.");
					_print.println("clear				- clears leakmanager mapped objects.");
					_print.println("class				- dumps TPM-class stats.");
					_print.println("announce <text>		- announces <text> in game.");
					_print.println("msg <nick> <text>	- Sends a whisper to char <nick> with <text>.");
					_print.println("gmchat <text>		- Sends a message to all GMs with <text>.");
					_print.println("gmlist				- lists all gms online.");
					_print.println("ip					- gets IP of player <name>.");
					_print.println("kick				- kick player <name> from server.");
					_print.println("shutdown <time>		- shuts down server in <time> seconds.");
					_print.println("restart <time>		- restarts down server in <time> seconds.");
					_print.println("abort				- aborts shutdown/restart.");
					_print.println("halt				- halts server.");
					_print.println("give <player> <itemid> <amount>");
					_print.println("enchant <player> <itemType> <enchant> (itemType: 1 - Helmet, 2 - Chest, 3 - Gloves, 4 - Feet, "
							+ "5 - Legs, 6 - Right Hand, 7 - Left Hand, 8 - Left Ear, 9 - Right Ear , 10 - Left Finger, 11 - Right Finger, "
							+ "12- Necklace, 13 - Underwear, 14 - Back, 15 - Belt, 0 - No Enchant)");
					_print.println("extreload <name>	- reload and initializes the named extension or all if used without argument");
					_print.println("extinit <name>		- initilizes the named extension or all if used without argument");
					_print.println("extunload <name>	- unload the named extension or all if used without argument");
					_print.println("debug <cmd>			- executes the debug command (see 'help debug').");
					_print.println("jail <player> [time]");
					_print.println("unjail <player>");
					_print.println("reload <...>");
					_print.println("reload_config <file>");
					_print.println("quit				- closes telnet session.");
				}
				else if (_usrCommand.equals("help debug"))
				{
					_print.println("The following is a list of all available debug commands: ");
					_print.println("decay			   - prints info about the DecayManager");
				}
				else if (_usrCommand.equals("status"))
				{
					int max = LoginLink.getInstance().getMaxPlayer();
					
					int playerCount = L2World.getInstance().getAllPlayersCount();
					int objectCount = L2World.getInstance().getAllVisibleObjectsCount();
					
					int itemCount = 0;
					int itemVoidCount = 0;
					int monsterCount = 0;
					int minionCount = 0;
					int minionsGroupCount = 0;
					int npcCount = 0;
					int pcCount = 0;
					int doorCount = 0;
					int summonCount = 0;
					int AICount = 0;
					
					for (L2Object obj : L2World.getInstance().getAllVisibleObjects())
					{
						if (obj == null)
							continue;
						if (obj instanceof L2Creature)
							if (((L2Creature)obj).hasAI())
								AICount++;
						
						if (obj instanceof L2ItemInstance)
						{
							if (((L2ItemInstance)obj).getLocation() == L2ItemInstance.ItemLocation.VOID)
								itemVoidCount++;
							else
								itemCount++;
						}
						else if (obj instanceof L2MonsterInstance)
						{
							monsterCount++;
							minionCount += ((L2MonsterInstance)obj).getTotalSpawnedMinionsInstances();
							minionsGroupCount += ((L2MonsterInstance)obj).getTotalSpawnedMinionsGroups();
						}
						else if (obj instanceof L2Npc)
							npcCount++;
						else if (obj instanceof L2Player)
							pcCount++;
						else if (obj instanceof L2Summon)
							summonCount++;
						else if (obj instanceof L2DoorInstance)
							doorCount++;
					}
					_print.println("Server Status: ");
					_print.println("  --->  Player Count: " + playerCount + "/" + max);
					_print.println("  +-->  Object Count: " + objectCount);
					_print.println("  +-->	  AI Count: " + AICount);
					_print.println("  +.... L2Item(Void): " + itemVoidCount);
					_print.println("  +.......... L2Item: " + itemCount);
					_print.println("  +....... L2Monster: " + monsterCount);
					_print.println("  +......... Minions: " + minionCount);
					_print.println("  +.. Minions Groups: " + minionsGroupCount);
					_print.println("  +........... L2Npc: " + npcCount);
					_print.println("  +............ L2Pc: " + pcCount);
					_print.println("  +........ L2Summon: " + summonCount);
					_print.println("  +.......... L2Door: " + doorCount);
					_print.println("  --->   Ingame Time: " + GameTimeManager.getInstance().getFormattedGameTime());
					_print.println("  ---> Server Uptime: " + getUptime(_uptime));
					_print.println("  --->  Database Pool: "
							+ L2DatabaseFactory.getInstance().getConnectionPoolStatus());
					_print.println("  --->	  GM Count: " + getOnlineGMS());
					_print.println("  --->	   Threads: " + Thread.activeCount());
					_print.println("  RAM Used: "
							+ ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576));
					_print.flush();
				}
				else if (_usrCommand.equals("printmemusage"))
				{
					for (String line : Util.getMemUsage())
					{
						_print.println(line);
					}
					_print.flush();
				}
				else if (_usrCommand.equals("performance"))
				{
					for (String line : ThreadPoolManager.getInstance().getStats())
					{
						_print.println(line);
					}
					_print.flush();
				}
				else if (_usrCommand.equals("threads"))
				{
					L2Thread.dumpThreads();
					_print.println("Threads dumped....");
					_print.flush();
				}
				else if (_usrCommand.equals("purge"))
				{
					ThreadPoolManager.getInstance().purge();
					_print.println("STATUS OF THREAD POOLS AFTER PURGE COMMAND:");
					_print.println("");
					for (String line : ThreadPoolManager.getInstance().getStats())
					{
						_print.println(line);
					}
					_print.flush();
				}
				else if (_usrCommand.startsWith("class"))
				{
					SortBy sortBy = null;
					try
					{
						sortBy = SortBy.valueOf(_usrCommand.substring(6).toUpperCase());
					}
					catch (Exception e)
					{
					}
					
					RunnableStatsManager.dumpClassStats(sortBy);
					_print.println("TPM-Classes dumped....");
					_print.flush();
				}
				else if (_usrCommand.equals("gc"))
				{
					for (String line : Util.getMemUsage())
					{
						_print.println(line);
					}
					_print.println("");
					_print.println("##################################");
					_print.print(" Garbage collecting... ");
					_print.flush();
					long act = System.currentTimeMillis();
					System.gc();
					_print.println("Done!");
					_print.println(" Duration: " + (System.currentTimeMillis() - act) + "msec...");
					_print.println("##################################");
					_print.println("");
					for (String line : Util.getMemUsage())
					{
						_print.println(line);
					}
					_print.flush();
				}
				else if (_usrCommand.equals("clean"))
				{
					_print.println("================================================================");
					long begin = System.currentTimeMillis();
					LeakTaskManager.getInstance().clean();
					_print.println("'clean' done in " + (System.currentTimeMillis() - begin) + "msec.");
					_print.println("================================================================");
				}
				else if (_usrCommand.equals("clear"))
				{
					_print.println("================================================================");
					long begin = System.currentTimeMillis();
					LeakTaskManager.getInstance().clear();
					_print.println("'clear' done in " + (System.currentTimeMillis() - begin) + "msec.");
					_print.println("================================================================");
				}
				else if (_usrCommand.startsWith("announce"))
				{
					try
					{
						_usrCommand = _usrCommand.substring(9);
						if (Config.ALT_TELNET && Config.ALT_TELNET_GM_ANNOUNCER_NAME)
							_usrCommand += " [" + _gm + "(offline)]";
						Announcements.getInstance().announceToAll(_usrCommand);
						_print.println("Announcement Sent!");
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please Enter Some Text To Announce!");
					}
				}
				else if (_usrCommand.startsWith("msg"))
				{
					try
					{
						String val = _usrCommand.substring(4);
						StringTokenizer st = new StringTokenizer(val);
						String name = st.nextToken();
						String message = val.substring(name.length() + 1);
						L2Player reciever = L2World.getInstance().getPlayer(name);
						CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Tell, "Telnet Priv", message);
						if (Config.ALT_TELNET)
							cs = new CreatureSay(0, SystemChatChannelId.Chat_Tell, _gm + "(offline)", message);
						if (reciever != null)
						{
							reciever.sendPacket(cs);
							_print.println("Telnet Priv->" + name + ": " + message);
							if (Config.ALT_TELNET)
								_print.println(_gm + "(offline): " + name + ": " + message);
							_print.println("Message Sent!");
						}
						else
						{
							_print.println("Unable To Find Username: " + name);
						}
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please Enter Some Text!");
					}
				}
				else if (_usrCommand.startsWith("gmchat"))
				{
					try
					{
						_usrCommand = _usrCommand.substring(7);
						CreatureSay cs =
								new CreatureSay(0, SystemChatChannelId.Chat_Alliance, "Telnet GM Broadcast from "
										+ _cSocket.getInetAddress().getHostAddress(), _usrCommand);
						GmListTable.broadcastToGMs(cs);
						_print.println("Your Message Has Been Sent To " + getOnlineGMS() + " GM(s).");
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please Enter Some Text To Announce!");
					}
				}
				else if (_usrCommand.equals("gmlist"))
				{
					int igm = 0;
					String gmList = "";
					
					for (String player : GmListTable.getAllGmNames(false))
					{
						gmList = gmList + ", " + player;
						igm++;
					}
					_print.println("There are currently " + igm + " GM(s) online...");
					if (!gmList.isEmpty())
						_print.println(gmList);
				}
				else if (_usrCommand.startsWith("ip"))
				{
					try
					{
						_usrCommand = _usrCommand.substring(3);
						L2Player player = L2World.getInstance().getPlayer(_usrCommand);
						if (player != null)
						{
							try
							{
								_print.println("IP of " + player + ": " + player.getClient().getHostAddress());
							}
							catch (RuntimeException e)
							{
								_print.println(e.toString());
							}
						}
						else
						{
							_print.println("No player online with that name!");
						}
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please enter player name to get IP");
					}
				}
				else if (_usrCommand.startsWith("kick"))
				{
					try
					{
						_usrCommand = _usrCommand.substring(5);
						L2Player player = L2World.getInstance().getPlayer(_usrCommand);
						if (player != null)
						{
							new Disconnection(player).defaultSequence(false);
							_print.println("Player kicked");
						}
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please enter player name to kick");
					}
				}
				else if (_usrCommand.startsWith("shutdown"))
				{
					try
					{
						int val = Integer.parseInt(_usrCommand.substring(9));
						Shutdown.start(_cSocket.getInetAddress().getHostAddress(), val, ShutdownMode.SHUTDOWN);
						_print.println("Server Will Shutdown In " + val + " Seconds!");
						_print.println("Type \"abort\" To Abort Shutdown!");
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please Enter * amount of seconds to shutdown!");
					}
					catch (NumberFormatException e)
					{
						_print.println("Numbers Only!");
					}
				}
				else if (_usrCommand.startsWith("restart"))
				{
					try
					{
						int val = Integer.parseInt(_usrCommand.substring(8));
						Shutdown.start(_cSocket.getInetAddress().getHostAddress(), val, ShutdownMode.RESTART);
						_print.println("Server Will Restart In " + val + " Seconds!");
						_print.println("Type \"abort\" To Abort Restart!");
					}
					catch (StringIndexOutOfBoundsException e)
					{
						_print.println("Please Enter * amount of seconds to restart!");
					}
					catch (Exception NumberFormatException)
					{
						_print.println("Numbers Only!");
					}
				}
				else if (_usrCommand.startsWith("abort"))
				{
					Shutdown.abort(_cSocket.getInetAddress().getHostAddress());
					_print.println("OK! - Shutdown/Restart Aborted.");
				}
				else if (_usrCommand.startsWith("halt"))
				{
					try
					{
						_print.print("Halting...");
						Shutdown.halt(_cSocket.getInetAddress().getHostAddress());
					}
					finally
					{
						_print.println("\t\t[OK]");
					}
				}
				else if (_usrCommand.equals("quit"))
				{
				}
				else if (_usrCommand.startsWith("give"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(5));
					
					String playername = st.nextToken();
					try
					{
						L2Player player = L2World.getInstance().getPlayer(playername);
						int itemId = Integer.parseInt(st.nextToken());
						int amount = Integer.parseInt(st.nextToken());
						
						if (player != null)
						{
							L2ItemInstance item =
									player.getInventory().addItem("Status-Give", itemId, amount, null, null);
							InventoryUpdate iu = new InventoryUpdate();
							iu.addItem(item);
							player.sendPacket(iu);
							SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
							sm.addItemName(item);
							sm.addNumber(amount);
							player.sendPacket(sm);
							_print.println("ok - was online");
						}
						else
						{
							Integer playerId = CharNameTable.getInstance().getByName(playername);
							if (playerId != null)
							{
								java.sql.Connection con = null;
								try
								{
									con = L2DatabaseFactory.getInstance().getConnection(con);
									addItemToInventory(con, playerId, IdFactory.getInstance().getNextId(), itemId,
											amount, 0);
								}
								finally
								{
									L2DatabaseFactory.close(con);
								}
								_print.println("ok - was offline");
							}
							else
							{
								_print.println("player not found");
							}
						}
					}
					catch (Exception e)
					{
						
					}
				}
				else if (_usrCommand.startsWith("enchant"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(8), " ");
					int enchant = 0, itemType = 0;
					
					try
					{
						L2Player player = L2World.getInstance().getPlayer(st.nextToken());
						itemType = Integer.parseInt(st.nextToken());
						enchant = Integer.parseInt(st.nextToken());
						
						switch (itemType)
						{
							case 1:
								itemType = Inventory.PAPERDOLL_HEAD;
								break;
							case 2:
								itemType = Inventory.PAPERDOLL_CHEST;
								break;
							case 3:
								itemType = Inventory.PAPERDOLL_GLOVES;
								break;
							case 4:
								itemType = Inventory.PAPERDOLL_FEET;
								break;
							case 5:
								itemType = Inventory.PAPERDOLL_LEGS;
								break;
							case 6:
								itemType = Inventory.PAPERDOLL_RHAND;
								break;
							case 7:
								itemType = Inventory.PAPERDOLL_LHAND;
								break;
							case 8:
								itemType = Inventory.PAPERDOLL_LEAR;
								break;
							case 9:
								itemType = Inventory.PAPERDOLL_REAR;
								break;
							case 10:
								itemType = Inventory.PAPERDOLL_LFINGER;
								break;
							case 11:
								itemType = Inventory.PAPERDOLL_RFINGER;
								break;
							case 12:
								itemType = Inventory.PAPERDOLL_NECK;
								break;
							case 13:
								itemType = Inventory.PAPERDOLL_UNDER;
								break;
							case 14:
								itemType = Inventory.PAPERDOLL_BACK;
								break;
							case 15:
								itemType = Inventory.PAPERDOLL_BELT;
								break;
							default:
								itemType = 0;
						}
						
						if (enchant > 65535)
							enchant = 65535;
						else if (enchant < 0)
							enchant = 0;
						
						boolean success = false;
						
						if (player != null && itemType > 0)
						{
							success = setEnchant(_cSocket, player, enchant, itemType);
							if (success)
								_print.println("Item enchanted successfully.");
						}
						else if (!success)
							_print.println("Item failed to enchant.");
					}
					catch (Exception e)
					{
						
					}
				}
				else if (_usrCommand.startsWith("jail"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(5));
					try
					{
						String name = st.nextToken();
						L2Player playerObj = L2World.getInstance().getPlayer(name);
						int delay = 0;
						try
						{
							delay = Integer.parseInt(st.nextToken());
						}
						catch (NumberFormatException nfe)
						{
						}
						catch (NoSuchElementException nsee)
						{
						}
						// L2Player playerObj =
						// L2World.getInstance().getPlayer(player);
						
						if (playerObj != null)
						{
							playerObj.setInJail(true, delay);
							_print.println("Character " + name + " jailed for "
									+ (delay > 0 ? delay + " minutes." : "ever!"));
						}
						else
							jailOfflinePlayer(name, delay);
					}
					catch (NoSuchElementException nsee)
					{
						_print.println("Specify a character name.");
					}
					catch (Exception e)
					{
						if (_log.isDebugEnabled())
							_log.error(e.getMessage(), e);
					}
				}
				else if (_usrCommand.startsWith("unjail"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(7));
					try
					{
						String name = st.nextToken();
						L2Player playerObj = L2World.getInstance().getPlayer(name);
						
						if (playerObj != null)
						{
							playerObj.stopJailTask(false);
							playerObj.setInJail(false, 0);
							_print.println("Character " + name + " removed from jail");
						}
						else
							unjailOfflinePlayer(name);
					}
					catch (NoSuchElementException nsee)
					{
						_print.println("Specify a character name.");
					}
					catch (Exception e)
					{
						if (_log.isDebugEnabled())
							_log.debug(e.getMessage(), e);
					}
				}
				else if (_usrCommand.startsWith("debug") && _usrCommand.length() > 6)
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(6));
					try
					{
						String dbg = st.nextToken();
						
						if (dbg.equals("decay"))
							_print.print(DecayTaskManager.getInstance().getStats());
					}
					catch (Exception e)
					{
					}
				}
				else if (_usrCommand.startsWith("reload_config"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand);
					st.nextToken();
					
					try
					{
						_print.println(L2AutoInitialization.loadConfig(st.nextToken()));
					}
					catch (Exception e)
					{
						_print.println("Usage:  reload_config <" + L2AutoInitialization.getLoaderNames() + ">");
					}
				}
				else if (_usrCommand.startsWith("reload"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand);
					st.nextToken();
					try
					{
						String type = st.nextToken();
						
						if (type.equals("multisell"))
						{
							_print.print("Reloading multisell... ");
							MultisellTable.getInstance().reload();
							_print.println("done");
						}
						else if (type.equals("teleport"))
						{
							_print.print("Reloading teleports... ");
							TeleportLocationTable.getInstance().reloadAll();
							_print.println("done");
						}
						else if (type.equals("skill"))
						{
							_print.print("Reloading skills... ");
							SkillTable.reload();
							_print.println("done");
						}
						else if (type.equals("npc"))
						{
							_print.print("Reloading npc templates... ");
							NpcTable.getInstance().cleanUp();
							NpcTable.getInstance().reloadAll();
							_print.println("done");
						}
						else if (type.equals("htm"))
						{
							_print.print("Reloading html cache... ");
							HtmCache.getInstance().reload(true);
							_print.println("done");
						}
						else if (type.equals("item"))
						{
							_print.print("Reloading item templates... ");
							ItemTable.reload();
							_print.println("done");
						}
						else if (type.equals("instancemanager"))
						{
							_print.print("Reloading instance managers... ");
							Manager.reloadAll();
							_print.println("done");
						}
						else if (type.equals("zone"))
						{
							_print.print("Reloading zone tables... ");
							ZoneManager.getInstance().reload();
							_print.println("done");
						}
						else if (type.equals("tradelist"))
						{
							_print.print("Reloading trade lists...");
							TradeListTable.getInstance().reloadAll();
							_print.println("done");
						}
						else if (type.startsWith("door"))
						{
							_print.print("Reloading Doors...");
							DoorTable.getInstance().reloadAll();
							_print.println("done");
						}
						else
						{
							_print.println("Usage: reload <multisell|teleport|skill|npc|htm|item|instancemanager|tradelist|zone|door>");
						}
					}
					catch (Exception e)
					{
						_print.println("Usage: reload <multisell|teleport|skill|npc|htm|item|instancemanager|tradelist|zone|door>");
					}
				}
				else if (_usrCommand.startsWith("gamestat"))
				{
					StringTokenizer st = new StringTokenizer(_usrCommand.substring(9));
					try
					{
						String type = st.nextToken();
						
						// name;type;x;y;itemId:enchant:price...
						if (type.equals("privatestore"))
						{
							for (L2Player player : L2World.getInstance().getAllPlayers())
							{
								if (player.getPrivateStoreType() == 0)
									continue;
								
								TradeList list = null;
								String content = "";
								
								if (player.getPrivateStoreType() == 1) // sell
								{
									list = player.getSellList();
									for (TradeItem item : list.getItems())
									{
										content +=
												item.getItem().getItemId() + ":" + item.getEnchant() + ":"
														+ item.getPrice() + ":";
									}
									content =
											player.getName() + ";" + "sell;" + player.getX() + ";" + player.getY()
													+ ";" + content;
									_print.println(content);
									continue;
								}
								else if (player.getPrivateStoreType() == 3) // buy
								{
									list = player.getBuyList();
									for (TradeItem item : list.getItems())
									{
										content +=
												item.getItem().getItemId() + ":" + item.getEnchant() + ":"
														+ item.getPrice() + ":";
									}
									content =
											player.getName() + ";" + "buy;" + player.getX() + ";" + player.getY() + ";"
													+ content;
									_print.println(content);
									continue;
								}
								
							}
						}
					}
					catch (Exception e)
					{
					}
				}
				else if (_usrCommand.startsWith("extreload"))
				{
					String[] args = _usrCommand.split("\\s+");
					if (args.length > 1)
					{
						for (int i = 1; i < args.length; i++)
							DynamicExtension.getInstance().reload(args[i]);
					}
					else
					{
						DynamicExtension.getInstance().reload();
					}
				}
				else if (_usrCommand.startsWith("extinit"))
				{
					String[] args = _usrCommand.split("\\s+");
					if (args.length > 1)
					{
						for (int i = 1; i < args.length; i++)
							DynamicExtension.getInstance().initExtension(args[i]);
					}
					else
					{
						DynamicExtension.getInstance().initExtensions();
					}
				}
				else if (_usrCommand.startsWith("extunload"))
				{
					String[] args = _usrCommand.split("\\s+");
					if (args.length > 1)
					{
						for (int i = 1; i < args.length; i++)
							DynamicExtension.getInstance().unloadExtension(args[i]);
					}
					else
					{
						DynamicExtension.getInstance().unloadExtensions();
					}
				}
				else if (_usrCommand.startsWith("get"))
				{
					Object o = null;
					try
					{
						String[] args = _usrCommand.substring(3).split("\\s+");
						if (args.length == 1)
							o = DynamicExtension.getInstance().get(args[0], null);
						else
							o = DynamicExtension.getInstance().get(args[0], args[1]);
					}
					catch (Exception ex)
					{
						_print.println(ex.toString());
					}
					if (o != null)
						_print.println(o.toString());
				}
				else if (_usrCommand.length() > 0)
				{
					try
					{
						String[] args = _usrCommand.split("\\s+");
						if (args.length == 1)
							DynamicExtension.getInstance().set(args[0], null, null);
						else if (args.length == 2)
							DynamicExtension.getInstance().set(args[0], null, args[1]);
						else
							DynamicExtension.getInstance().set(args[0], args[1], args[2]);
					}
					catch (Exception ex)
					{
						_print.print(ex.toString());
					}
				}
				else if (_usrCommand.length() == 0)
				{
				}
				_print.print("");
				_print.flush();
			}
			if (!_cSocket.isClosed())
			{
				_print.println("Bye Bye!");
				_print.flush();
				_cSocket.close();
			}
			telnetOutput(1, "Connection from " + _cSocket.getInetAddress().getHostAddress() + " was closed by client.");
		}
		catch (IOException e)
		{
			_log.error(e.getMessage(), e);
		}
	}
	
	private boolean setEnchant(Socket gm, L2Player activeChar, int ench, int armorType)
	{
		// now we need to find the equipped weapon of the targeted character...
		int curEnchant = 0; // display purposes only
		L2ItemInstance itemInstance = null;
		
		// only attempt to enchant if there is a weapon equipped
		L2ItemInstance parmorInstance = activeChar.getInventory().getPaperdollItem(armorType);
		if (parmorInstance != null && parmorInstance.getLocationSlot() == armorType)
		{
			itemInstance = parmorInstance;
		}
		else
		{
			// for bows/crossbows and double handed weapons
			parmorInstance = activeChar.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LRHAND);
			if (parmorInstance != null && parmorInstance.getLocationSlot() == Inventory.PAPERDOLL_LRHAND)
				itemInstance = parmorInstance;
		}
		
		if (itemInstance != null)
		{
			curEnchant = itemInstance.getEnchantLevel();
			
			// set enchant value
			activeChar.getInventory().unEquipItemInSlotAndRecord(armorType);
			itemInstance.setEnchantLevel(ench);
			activeChar.getInventory().equipItemAndRecord(itemInstance);
			
			// send packets
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(itemInstance);
			activeChar.sendPacket(iu);
			activeChar.broadcastUserInfo();
			
			// informations
			activeChar.sendMessage("Changed enchantment of " + activeChar.getName() + "'s "
					+ itemInstance.getItem().getName() + " from " + curEnchant + " to " + ench + ".");
			activeChar.sendMessage("Admin has changed the enchantment of your " + itemInstance.getItem().getName()
					+ " from " + curEnchant + " to " + ench + ".");
			
			String IP = gm.getInetAddress().getHostAddress();
			// log
			GMAudit.auditGMAction(IP, activeChar.getName(), "telnet-enchant", "telnet-enchant", itemInstance.getItem()
					.getName() + "(" + itemInstance.getObjectId() + ")" + " from " + curEnchant + " to " + ench);
			return true;
		}
		return false;
	}
	
	private void jailOfflinePlayer(String name, int delay)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement =
					con.prepareStatement("UPDATE player SET x = ?, y = ?, z = ?, is_in_jail = ?, jail_remaining_ms = ? WHERE name = ?");
			statement.setInt(1, L2JailZone.JAIL_LOCATION.getX());
			statement.setInt(2, L2JailZone.JAIL_LOCATION.getY());
			statement.setInt(3, L2JailZone.JAIL_LOCATION.getZ());
			statement.setBoolean(4, true);
			statement.setLong(5, delay * 60000L);
			statement.setString(6, name);
			
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			
			if (count == 0)
				_print.println("Character not found!");
			else
				_print.println("Character " + name + " jailed for " + (delay > 0 ? delay + " minutes." : "ever!"));
		}
		catch (SQLException se)
		{
			_print.println("SQLException while jailing player");
			if (_log.isDebugEnabled())
				_log.warn("SQLException while jailing player", se);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void unjailOfflinePlayer(String name)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement =
					con.prepareStatement("UPDATE player SET x = ?, y = ?, z = ?, is_in_jail = ?, jail_remaining_ms = ? WHERE name = ?");
			statement.setInt(1, 17836);
			statement.setInt(2, 170178);
			statement.setInt(3, -3507);
			statement.setBoolean(4, false);
			statement.setLong(5, 0);
			statement.setString(6, name);
			
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			
			if (count == 0)
				_print.println("Character not found!");
			else
				_print.println("Character " + name + " set free.");
		}
		catch (SQLException se)
		{
			_print.println("SQLException while jailing player");
			if (_log.isDebugEnabled())
				_log.warn("SQLException while jailing player", se);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private int getOnlineGMS()
	{
		return GmListTable.getAllGms(true).size();
	}
	
	private String getUptime(long time)
	{
		long uptime = System.currentTimeMillis() - time;
		uptime = uptime / 1000;
		long h = uptime / 3600;
		long m = (uptime - (h * 3600)) / 60;
		long s = ((uptime - (h * 3600)) - (m * 60));
		return h + "hrs " + m + "mins " + s + "secs";
	}
	
	private void addItemToInventory(java.sql.Connection con, int charId, int objectId, int currency, long count,
			int enchantLevel) throws SQLException
	{
		PreparedStatement statement =
				con.prepareStatement("INSERT INTO item (owner_player_id, id, item_template_id, count, enchant_level, location, location_slot) VALUES (?, ?, ?, ?, ?, ?, ?)");
		statement.setInt(1, charId);
		statement.setInt(2, objectId);
		statement.setInt(3, currency);
		statement.setLong(4, count);
		statement.setInt(5, enchantLevel);
		statement.setString(6, "INVENTORY");
		statement.setInt(7, 0);
		statement.execute();
		statement.close();
	}
	
	static
	{
		ListeningLog.addListener(new LogListener() {
			@Override
			public void write(String s)
			{
				if (Thread.currentThread() instanceof GameStatusThread)
				{
					final GameStatusThread gst = (GameStatusThread)Thread.currentThread();
					
					gst._print.println(s);
					gst._print.flush();
				}
			}
		});
	}
}
