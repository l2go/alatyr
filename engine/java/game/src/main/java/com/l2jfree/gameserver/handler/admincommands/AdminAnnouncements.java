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
package com.l2jfree.gameserver.handler.admincommands;

import java.util.concurrent.ScheduledFuture;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.AutoAnnouncements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;

/**
 * This class handles following admin commands:
 * - announce text = announces text to all players
 * - list_announcements = show menu
 * - reload_announcements = reloads announcements from txt file
 * - announce_announcements = announce all stored announcements to all players
 * - add_announcement text = adds text to startup announcements
 * - del_announcement id = deletes announcement with respective id
 * 
 * @version $Revision: 1.4.4.5 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminAnnouncements implements IAdminCommandHandler
{
	private static final int CRITICAL_INTERVAL = 60000;
	private static final String[] ADMIN_COMMANDS = { "admin_list_announcements", "admin_reload_announcements",
			"admin_announce_announcements", "admin_add_announcement", "admin_del_announcement", "admin_announce",
			"admin_announce_menu", "admin_reload_autoannounce", "admin_disable_chat", "admin_enable_chat", "admin_ca",
			"admin_anno" };
	private static ScheduledFuture<?> _chatReEnable = null;
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_list_announcements"))
		{
			Announcements.getInstance().listAnnouncements(activeChar);
		}
		else if (command.equals("admin_reload_announcements"))
		{
			Announcements.getInstance().loadAnnouncements();
			Announcements.getInstance().listAnnouncements(activeChar);
		}
		else if (command.startsWith("admin_announce_menu"))
		{
			Announcements.getInstance().handleAnnounce(command, 20);
			Announcements.getInstance().listAnnouncements(activeChar);
		}
		else if (command.equals("admin_announce_announcements"))
		{
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				Announcements.getInstance().showAnnouncements(player);
			}
			Announcements.getInstance().listAnnouncements(activeChar);
		}
		else if (command.startsWith("admin_add_announcement"))
		{
			if (!command.equals("admin_add_announcement"))
			{
				try
				{
					String val = command.substring(23);
					Announcements.getInstance().addAnnouncement(val);
					Announcements.getInstance().listAnnouncements(activeChar);
				}
				catch (StringIndexOutOfBoundsException e)
				{
				}//ignore errors
			}
		}
		else if (command.startsWith("admin_del_announcement"))
		{
			try
			{
				int val = Integer.valueOf(command.substring(23));
				Announcements.getInstance().delAnnouncement(val);
				Announcements.getInstance().listAnnouncements(activeChar);
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		
		// Command is admin announce
		else if (command.startsWith("admin_announce"))
		{
			// Call method from another class
			if (Config.GM_ANNOUNCER_NAME)
				command += " [" + activeChar.getName() + "]";
			Announcements.getInstance().handleAnnounce(command, 15);
		}
		else if (command.startsWith("admin_reload_autoannounce"))
		{
			activeChar.sendMessage("AutoAnnouncement Reloaded.");
			AutoAnnouncements.getInstance().reload();
		}
		else if (command.startsWith("admin_anno"))
		{
			if (Config.GM_ANNOUNCER_NAME)
				command += " [" + activeChar.getName() + "]";
			Announcements.getInstance().handleAnnounce(command, 11);
		}
		else if (command.startsWith("admin_ca"))
		{
			if (!Config.DISABLE_ALL_CHAT)
			{
				activeChar.sendMessage("You must //disable_chat before using this!");
				return false;
			}
			if (Config.GM_ANNOUNCER_NAME)
				command += " [" + activeChar.getName() + "]";
			CreatureSay cs =
					new CreatureSay(activeChar.getObjectId(), SystemChatChannelId.Chat_Critical_Announce,
							activeChar.getName(), command.replace("admin_ca", "**"));
			Announcements.getInstance().announceToAll(cs);
		}
		else if (command.startsWith("admin_disable_chat"))
		{
			if (!Config.DISABLE_ALL_CHAT)
			{
				Config.DISABLE_ALL_CHAT = true;
				Announcements.getInstance().announceToAll(SystemMessageId.CHAT_DISABLED);
			}
			else
				_chatReEnable.cancel(true);
			_chatReEnable = ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
				@Override
				public void run()
				{
					enableChat();
				}
			}, CRITICAL_INTERVAL);
			activeChar.sendMessage("You have " + CRITICAL_INTERVAL / 1000
					+ " seconds for your announcements, repeat the command to reset timer.");
			activeChar.sendMessage("//enable_chat to re-enable chat before timer expires.");
		}
		else if (command.startsWith("admin_enable_chat"))
		{
			if (Config.DISABLE_ALL_CHAT)
			{
				_chatReEnable.cancel(true);
				_chatReEnable = null;
				enableChat();
			}
		}
		
		return true;
	}
	
	private void enableChat()
	{
		Config.DISABLE_ALL_CHAT = false;
		Announcements.getInstance().announceToAll(SystemMessageId.CHAT_ENABLED);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
