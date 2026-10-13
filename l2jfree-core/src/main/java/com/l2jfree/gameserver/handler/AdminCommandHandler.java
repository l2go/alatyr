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
package com.l2jfree.gameserver.handler;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.admincommands.AdminAI;
import com.l2jfree.gameserver.handler.admincommands.AdminAdmin;
import com.l2jfree.gameserver.handler.admincommands.AdminAnnouncements;
import com.l2jfree.gameserver.handler.admincommands.AdminBBS;
import com.l2jfree.gameserver.handler.admincommands.AdminBan;
import com.l2jfree.gameserver.handler.admincommands.AdminBanChat;
import com.l2jfree.gameserver.handler.admincommands.AdminBoat;
import com.l2jfree.gameserver.handler.admincommands.AdminBuffs;
import com.l2jfree.gameserver.handler.admincommands.AdminCTFEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminCache;
import com.l2jfree.gameserver.handler.admincommands.AdminCamera;
import com.l2jfree.gameserver.handler.admincommands.AdminChangeAccessLevel;
import com.l2jfree.gameserver.handler.admincommands.AdminContest;
import com.l2jfree.gameserver.handler.admincommands.AdminCreateItem;
import com.l2jfree.gameserver.handler.admincommands.AdminCursedWeapons;
import com.l2jfree.gameserver.handler.admincommands.AdminDMEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminDelete;
import com.l2jfree.gameserver.handler.admincommands.AdminDoorControl;
import com.l2jfree.gameserver.handler.admincommands.AdminEditChar;
import com.l2jfree.gameserver.handler.admincommands.AdminEditNpc;
import com.l2jfree.gameserver.handler.admincommands.AdminEffects;
import com.l2jfree.gameserver.handler.admincommands.AdminElement;
import com.l2jfree.gameserver.handler.admincommands.AdminEnchant;
import com.l2jfree.gameserver.handler.admincommands.AdminExpSp;
import com.l2jfree.gameserver.handler.admincommands.AdminFightCalculator;
import com.l2jfree.gameserver.handler.admincommands.AdminFortSiege;
import com.l2jfree.gameserver.handler.admincommands.AdminGeoEditor;
import com.l2jfree.gameserver.handler.admincommands.AdminGeodata;
import com.l2jfree.gameserver.handler.admincommands.AdminGm;
import com.l2jfree.gameserver.handler.admincommands.AdminGmChat;
import com.l2jfree.gameserver.handler.admincommands.AdminHeal;
import com.l2jfree.gameserver.handler.admincommands.AdminHellbound;
import com.l2jfree.gameserver.handler.admincommands.AdminHelpPage;
import com.l2jfree.gameserver.handler.admincommands.AdminInstance;
import com.l2jfree.gameserver.handler.admincommands.AdminInvul;
import com.l2jfree.gameserver.handler.admincommands.AdminJail;
import com.l2jfree.gameserver.handler.admincommands.AdminKick;
import com.l2jfree.gameserver.handler.admincommands.AdminKill;
import com.l2jfree.gameserver.handler.admincommands.AdminLevel;
import com.l2jfree.gameserver.handler.admincommands.AdminLogin;
import com.l2jfree.gameserver.handler.admincommands.AdminMammon;
import com.l2jfree.gameserver.handler.admincommands.AdminManor;
import com.l2jfree.gameserver.handler.admincommands.AdminMenu;
import com.l2jfree.gameserver.handler.admincommands.AdminMobGroup;
import com.l2jfree.gameserver.handler.admincommands.AdminMonsterRace;
import com.l2jfree.gameserver.handler.admincommands.AdminPForge;
import com.l2jfree.gameserver.handler.admincommands.AdminPetition;
import com.l2jfree.gameserver.handler.admincommands.AdminPledge;
import com.l2jfree.gameserver.handler.admincommands.AdminPolymorph;
import com.l2jfree.gameserver.handler.admincommands.AdminQuest;
import com.l2jfree.gameserver.handler.admincommands.AdminRegion;
import com.l2jfree.gameserver.handler.admincommands.AdminRepairChar;
import com.l2jfree.gameserver.handler.admincommands.AdminRes;
import com.l2jfree.gameserver.handler.admincommands.AdminRide;
import com.l2jfree.gameserver.handler.admincommands.AdminSHEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminSendHome;
import com.l2jfree.gameserver.handler.admincommands.AdminShop;
import com.l2jfree.gameserver.handler.admincommands.AdminShutdown;
import com.l2jfree.gameserver.handler.admincommands.AdminSiege;
import com.l2jfree.gameserver.handler.admincommands.AdminSkill;
import com.l2jfree.gameserver.handler.admincommands.AdminSmartShop;
import com.l2jfree.gameserver.handler.admincommands.AdminSortMultisellItems;
import com.l2jfree.gameserver.handler.admincommands.AdminSpawn;
import com.l2jfree.gameserver.handler.admincommands.AdminSummon;
import com.l2jfree.gameserver.handler.admincommands.AdminTarget;
import com.l2jfree.gameserver.handler.admincommands.AdminTeleport;
import com.l2jfree.gameserver.handler.admincommands.AdminTest;
import com.l2jfree.gameserver.handler.admincommands.AdminTvTEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminTvTiEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminUnblockIp;
import com.l2jfree.gameserver.handler.admincommands.AdminVIPEngine;
import com.l2jfree.gameserver.handler.admincommands.AdminVitality;
import com.l2jfree.gameserver.handler.admincommands.AdminZone;
import com.l2jfree.gameserver.model.GMAudit;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.util.HandlerRegistry;
import com.l2jfree.util.logging.ListeningLog;
import com.l2jfree.util.logging.ListeningLog.LogListener;

public final class AdminCommandHandler extends HandlerRegistry<String, IAdminCommandHandler>
{
	public static AdminCommandHandler getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private AdminCommandHandler()
	{
		register(new AdminAI());
		register(new AdminAdmin());
		register(new AdminAnnouncements());
		register(new AdminBBS());
		register(new AdminBan());
		register(new AdminBanChat());
		register(new AdminBoat());
		register(new AdminBuffs());
		register(new AdminCTFEngine());
		register(new AdminCache());
		register(new AdminCamera());
		register(new AdminChangeAccessLevel());
		register(new AdminContest());
		register(new AdminCreateItem());
		register(new AdminCursedWeapons());
		register(new AdminDMEngine());
		register(new AdminDelete());
		register(new AdminDoorControl());
		register(new AdminEditChar());
		register(new AdminEditNpc());
		register(new AdminEffects());
		register(new AdminElement());
		register(new AdminEnchant());
		register(new AdminExpSp());
		register(new AdminFightCalculator());
		register(new AdminFortSiege());
		register(new AdminGeoEditor());
		register(new AdminGeodata());
		register(new AdminGm());
		register(new AdminGmChat());
		register(new AdminHeal());
		register(new AdminHellbound());
		register(new AdminHelpPage());
		register(new AdminInstance());
		register(new AdminInvul());
		register(new AdminJail());
		register(new AdminKick());
		register(new AdminKill());
		register(new AdminLevel());
		register(new AdminLogin());
		register(new AdminMammon());
		register(new AdminManor());
		register(new AdminMenu());
		register(new AdminMobGroup());
		register(new AdminMonsterRace());
		register(new AdminPForge());
		register(new AdminPetition());
		register(new AdminPledge());
		register(new AdminPolymorph());
		register(new AdminQuest());
		register(new AdminRegion());
		register(new AdminRepairChar());
		register(new AdminRes());
		register(new AdminRide());
		register(new AdminSHEngine());
		register(new AdminSendHome());
		register(new AdminShop());
		register(new AdminShutdown());
		register(new AdminSiege());
		register(new AdminSkill());
		register(new AdminSmartShop());
		register(new AdminSortMultisellItems());
		register(new AdminSpawn());
		register(new AdminSummon());
		register(new AdminTarget());
		register(new AdminTeleport());
		register(new AdminTest());
		register(new AdminTvTEngine());
		register(new AdminTvTiEngine());
		register(new AdminUnblockIp());
		register(new AdminVIPEngine());
		register(new AdminVitality());
		register(new AdminZone());
		
		// Dynamic testing extensions
		try
		{
			register((IAdminCommandHandler)Class.forName(
					"com.l2jfree.gameserver.handler.admincommandhandlers.AdminRuntimeTest").newInstance());
		}
		catch (Throwable t)
		{
		}
		
		_log.info("AdminCommandHandler: Loaded " + size() + " handlers.");
		
		for (String cmd : Config.GM_COMMAND_PRIVILEGES.keySet())
			if (get(cmd) == null)
				_log.info("AdminCommandHandler: Command \"" + cmd + "\" isn't used anymore.");
	}
	
	private void register(IAdminCommandHandler handler)
	{
		registerAll(handler, handler.getAdminCommandList());
		
		for (String element : handler.getAdminCommandList())
			if (!Config.GM_COMMAND_PRIVILEGES.containsKey(element))
				_log.warn("Command \"" + element + "\" have no access level definition. Can't be used.");
	}
	
	public void useAdminCommand(final L2Player activeChar, String message0)
	{
		final String message = message0.trim();
		
		String command = message;
		String params = "";
		
		if (message.indexOf(" ") != -1)
		{
			command = message.substring(0, message.indexOf(" "));
			params = message.substring(message.indexOf(" ") + 1);
		}
		
		command = command.trim().toLowerCase();
		params = params.trim();
		
		if (!activeChar.isGM() && !command.equals("admin_gm"))
		{
			Util.handleIllegalPlayerAction(activeChar, "AdminCommandHandler: A non-gm request.", Config.DEFAULT_PUNISH);
			return;
		}
		
		final IAdminCommandHandler handler = get(command);
		
		if (handler == null)
		{
			activeChar.sendMessage("No handler registered.");
			_log.warn("No handler registered for bypass '" + message + "'");
			return;
		}
		
		if (!Config.GM_COMMAND_PRIVILEGES.containsKey(command))
		{
			activeChar.sendMessage("It has no access level definition. It can't be used.");
			_log.warn(message + "' has no access level definition. It can't be used.");
			return;
		}
		
		if (activeChar.getAccessLevel() < Config.GM_COMMAND_PRIVILEGES.get(command))
		{
			activeChar.sendMessage("You don't have sufficient privileges.");
			_log.warn(activeChar + " does not have sufficient privileges for '" + message + "'.");
			return;
		}
		
		GMAudit.auditGMAction(activeChar, "admincommand", command, params);
		
		final Future<?> task = ThreadPoolManager.getInstance().submitLongRunning(new Runnable() {
			@Override
			public void run()
			{
				final long begin = System.currentTimeMillis();
				try
				{
					runAs(activeChar::sendMessage, () -> handler.useAdminCommand(message, activeChar));
				}
				catch (RuntimeException e)
				{
					activeChar.sendMessage("Exception during execution of  '" + message + "': " + e.toString());
					
					throw e;
				}
				finally
				{
					final long runtime = System.currentTimeMillis() - begin;
					
					if (runtime < ThreadPoolManager.MAXIMUM_RUNTIME_IN_MILLISEC_WITHOUT_WARNING)
						return;
					
					activeChar.sendMessage("The execution of '" + message + "' took " + Util.formatNumber(runtime)
							+ " msec.");
				}
			}
		});
		
		try
		{
			task.get(1000, TimeUnit.MILLISECONDS);
			return;
		}
		catch (Exception e)
		{
			activeChar.sendMessage("The execution of '" + message
					+ "' takes more time than 1000 msec, so execution done asynchronusly.");
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AdminCommandHandler _instance = new AdminCommandHandler();
	}
	
	/** Messages to the GM whose command runs on this thread; log lines written meanwhile go there as well. */
	private static final ScopedValue<Consumer<String>> ACTIVE_GM = ScopedValue.newInstance();
	
	static
	{
		ListeningLog.addListener(new LogListener() {
			@Override
			public void write(String s)
			{
				if (ACTIVE_GM.isBound())
					ACTIVE_GM.get().accept(s);
			}
		});
	}
	
	static void runAs(Consumer<String> gm, Runnable command)
	{
		ScopedValue.where(ACTIVE_GM, gm).run(command);
	}
}
