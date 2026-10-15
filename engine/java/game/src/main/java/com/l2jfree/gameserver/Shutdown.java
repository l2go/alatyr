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
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.ItemsOnGroundManager;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.hellbound.HellboundManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.ArenaManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.FishermanManager;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2ClientSelectorThread;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.taskmanager.SQLQueue;
import com.l2jfree.gameserver.util.OfflineTradeManager;

/**
 * This class provides the functions for shutting down and restarting the server It closes all open clientconnections
 * and saves all data.
 * 
 * @version $Revision: 1.2.4.5 $ $Date: 2005/03/27 15:29:09 $
 */
public final class Shutdown extends Thread
{
	public static enum ShutdownMode
	{
		NONE("terminating"),
		SHUTDOWN("shutting down"),
		RESTART("restarting");
		
		private final String _text;
		
		private ShutdownMode(String text)
		{
			_text = text;
		}
		
		private String getText()
		{
			return _text;
		}
	}
	
	private static final Logger _log = LoggerFactory.getLogger(Shutdown.class);
	
	// written by the admin and countdown threads, read by the game threads and by the countdown loop
	private static volatile int _counter = Integer.MAX_VALUE;
	private static volatile ShutdownMode _mode = ShutdownMode.NONE;
	
	private static volatile Shutdown _counterInstance;
	private static volatile Shutdown _hookInstance;
	
	public static synchronized Shutdown getInstance()
	{
		if (_hookInstance == null)
			_hookInstance = new Shutdown();
		
		return _hookInstance;
	}
	
	private Shutdown()
	{
	}
	
	@Override
	public void run()
	{
		if (this == _counterInstance)
			countdown();
		else if (this == _hookInstance)
			shutdownHook();
	}
	
	private void countdown()
	{
		while (_counter > 0 && this == _counterInstance)
		{
			if (_counter <= 30 || _counter <= 600 && _counter % 60 == 0 || _counter <= 3600 && _counter % 300 == 0)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.THE_SERVER_WILL_BE_COMING_DOWN_IN_S1_SECONDS);
				Announcements.getInstance().announceToAll(sm.addNumber(_counter));
			}
			
			try
			{
				if (_counter <= 60)
					LoginLink.getInstance().setServerStatusDown();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			_counter--;
			
			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		
		// shutdown aborted
		if (this != _counterInstance)
			return;
		
		// last point where logging is operational :(
		_log.warn("Shutdown countdown is over: " + _mode.getText() + " NOW!");
		
		if (_mode == ShutdownMode.RESTART)
			System.exit(2);
		else
			System.exit(0);
	}
	
	private void shutdownHook()
	{
		// nobody enters while the players are saved: the players that are in stay until their turn
		try
		{
			L2ClientSelectorThread.getInstance().stopAccepting();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		try
		{
			Announcements.getInstance().announceToAll("Server is " + _mode.getText() + " NOW!");
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		OfflineTradeManager.getInstance().store();
		
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			try
			{
				new Disconnection(player).defaultSequence(true);
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}
		}
		
		// Seven Signs data is now saved along with Festival data.
		if (!SevenSigns.getInstance().isSealValidationPeriod())
			SevenSignsFestival.getInstance().saveFestivalData(false);
		
		// Save restrictions that are apllied to L2Objects
		ObjectRestrictions.getInstance().shutdown();
		// Save Seven Signs data before closing. :)
		SevenSigns.getInstance().saveSevenSignsData(null, true);
		System.out.println("SevenSigns: Data saved.");
		RaidBossSpawnManager.getInstance().cleanUp();
		System.out.println("RaidBossSpawnManager: All raidboss info saved.");
		GrandBossSpawnManager.getInstance().cleanUp();
		System.out.println("GrandBossSpawnManager: All grandboss info saved.");
		System.out.println("Saving TradeController data, please wait...");
		TradeListTable.getInstance().dataCountStore();
		System.out.println("TradeController: All count Item Saved");
		Olympiad.getInstance().saveOlympiadStatus();
		System.out.println("Olympiad System: Data saved!!");
		
		// Save all manor data
		CastleManorManager.getInstance().save();
		
		// Save all global (non-player specific) Quest data that needs to persist after reboot
		QuestManager.getInstance().save();
		
		// Save Arena Data if enabled
		if (Config.ARENA_ENABLED)
		{
			ArenaManager.getInstance().stopSaveTask();
			ArenaManager.getInstance().saveData();
		}
		
		// Save Fishing Data if enabled
		if (Config.FISHERMAN_ENABLED)
		{
			FishermanManager.getInstance().stopSaveTask();
			FishermanManager.getInstance().saveData();
		}
		
		// Save Cursed Weapons data before closing.
		CursedWeaponsManager.getInstance().saveData();
		System.out.println("CursedWeaponsManager: Data saved.");
		// Save items on ground before closing
		if (Config.SAVE_DROPPED_ITEM)
		{
			ItemsOnGroundManager.getInstance().saveInDb();
			ItemsOnGroundManager.getInstance().cleanUp();
			System.out.println("ItemsOnGroundManager: All items on ground saved.");
		}
		if (MercTicketManager.getInstance().stopSaveTask())
			MercTicketManager.getInstance().saveAll();
		
		HellboundManager.getInstance().saveToDB();
		System.out.println("HellboundManager: Current state saved.");
		
		SQLQueue.getInstance().run();
		System.out.println("Data saved. All players disconnected, " + _mode.getText() + ".");
		
		try
		{
			Thread.sleep(1000);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		try
		{
			GameTimeManager.stopTimer();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		try
		{
			L2ClientSelectorThread.getInstance().shutdown();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		try
		{
			ThreadPoolManager.getInstance().shutdown();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		// the periodic flush is stopped with the pools: write what a task queued in the meantime
		try
		{
			SQLQueue.getInstance().run();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		try
		{
			L2DatabaseFactory.getInstance().shutdown();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		
		
		if (_mode == ShutdownMode.RESTART)
			Runtime.getRuntime().halt(2);
		else
			Runtime.getRuntime().halt(0);
	}
	
	public static void start(String initiator, int seconds, ShutdownMode mode)
	{
		_log.warn(initiator + " issued shutdown command: " + mode.getText() + " in " + seconds + " seconds!");
		
		String msg = "Server is " + mode.getText() + " in " + seconds + " seconds!";
		
		Announcements.getInstance().announceToAll("Attention players!");
		Announcements.getInstance().announceToAll(msg);
		
		_counter = seconds;
		_mode = mode;
		
		_counterInstance = new Shutdown();
		_counterInstance.start();
	}
	
	public static void abort(String initiator)
	{
		_log.warn(initiator + " issued shutdown abort: " + _mode.getText() + " has been stopped!");
		
		String msg = "Server aborts " + _mode.getText() + " and continues normal operation!";
		
		Announcements.getInstance().announceToAll(msg);
		
		_counter = Integer.MAX_VALUE;
		_mode = ShutdownMode.NONE;
		
		_counterInstance = null;
	}
	
	public static void halt(String initiator)
	{
		try
		{
			System.out.println(initiator + " issued HALT command: " + _mode.getText() + " has been stopped!");
		}
		finally
		{
			Runtime.getRuntime().halt(2);
		}
	}
	
	public static boolean isActionDisabled(DisableType type)
	{
		return type.isDisabled() && Config.SAFE_REBOOT && _counterInstance != null
				&& _counter <= Config.SAFE_REBOOT_TIME;
	}
	
	public static enum DisableType
	{
		ENCHANT,
		TELEPORT,
		CREATEITEM,
		TRANSACTION,
		PC_ITERACTION,
		NPC_ITERACTION;
		
		private boolean isDisabled()
		{
			switch (this)
			{
				case ENCHANT:
					return Config.SAFE_REBOOT_DISABLE_ENCHANT;
				case TELEPORT:
					return Config.SAFE_REBOOT_DISABLE_TELEPORT;
				case CREATEITEM:
					return Config.SAFE_REBOOT_DISABLE_CREATEITEM;
				case TRANSACTION:
					return Config.SAFE_REBOOT_DISABLE_TRANSACTION;
				case PC_ITERACTION:
					return Config.SAFE_REBOOT_DISABLE_PC_ITERACTION;
				case NPC_ITERACTION:
					return Config.SAFE_REBOOT_DISABLE_NPC_ITERACTION;
				default:
					throw new InternalError();
			}
		}
	}
	
	public static boolean isInProgress()
	{
		return _counterInstance != null;
	}
}
