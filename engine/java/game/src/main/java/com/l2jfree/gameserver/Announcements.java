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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.AnnouncementStore;
import com.l2jfree.gameserver.script.DateRange;

/**
 * This class ...
 * 
 * @version $Revision: 1.5.2.1.2.7 $ $Date: 2005/03/29 23:15:14 $
 */
public class Announcements
{
	private final static Logger _log = LoggerFactory.getLogger(Announcements.class);
	
	private final List<String> _announcements = new ArrayList<String>();
	private final List<List<Object>> _eventAnnouncements = new ArrayList<List<Object>>();
	
	private Announcements()
	{
		loadAnnouncements();
	}
	
	public static Announcements getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public void loadAnnouncements()
	{
		_announcements.clear();
		_announcements.addAll(AnnouncementStore.load());
	}
	
	public void showAnnouncements(L2Player activeChar)
	{
		for (int i = 0; i < _announcements.size(); i++)
		{
			CreatureSay cs =
					new CreatureSay(0, SystemChatChannelId.Chat_Announce, activeChar.getName(), _announcements.get(i)
							.replace("%name%", activeChar.getName()));
			activeChar.sendPacket(cs);
		}
		
		Date currentDate = new Date();
		for (int i = 0; i < _eventAnnouncements.size(); i++)
		{
			List<Object> entry = _eventAnnouncements.get(i);
			
			DateRange validDateRange = (DateRange)entry.get(0);
			String[] msg = (String[])entry.get(1);
			
			if (validDateRange.isValid() && validDateRange.isWithinRange(currentDate))
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1);
				for (String element : msg)
					sm.addString(element);
				activeChar.sendPacket(sm);
			}
		}
	}
	
	public void addEventAnnouncement(DateRange validDateRange, String[] msg)
	{
		ArrayList<Object> entry = new ArrayList<Object>();
		entry.add(validDateRange);
		entry.add(msg);
		entry.trimToSize();
		_eventAnnouncements.add(entry);
	}
	
	public void listAnnouncements(L2Player activeChar)
	{
		String content = HtmCache.getInstance().getHtmForce("data/html/admin/announce.htm");
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setHtml(content);
		StringBuilder replyMSG = new StringBuilder();
		replyMSG.append("<br>");
		for (int i = 0; i < _announcements.size(); i++)
		{
			replyMSG.append("<table width=260><tr><td width=220>");
			replyMSG.append(_announcements.get(i));
			replyMSG.append("</td><td width=40><button value=\"Delete\" action=\"bypass -h admin_del_announcement ");
			replyMSG.append(i);
			replyMSG.append("\" width=60 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr></table>");
		}
		adminReply.replace("%announces%", replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	public void addAnnouncement(String text)
	{
		_announcements.add(text);
		saveToDatabase();
	}
	
	public void delAnnouncement(int line)
	{
		_announcements.remove(line);
		saveToDatabase();
	}
	
	private void saveToDatabase()
	{
		AnnouncementStore.replaceAll(_announcements);
	}
	
	public void announceToAll(String text)
	{
		CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Announce, "", text);
		
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(cs);
		}
	}
	
	public void announceToAll(L2ServerPacket gsp)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(gsp);
		}
	}
	
	public void announceToAll(SystemMessageId sm)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			player.sendPacket(sm);
		}
	}
	
	public void announceToInstance(L2ServerPacket gsp, int instanceId)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
		{
			if (player.isSameInstance(instanceId))
				player.sendPacket(gsp);
		}
	}
	
	// Method fo handling announcements from admin
	public void handleAnnounce(String command, int lengthToTrim)
	{
		try
		{
			// Announce string to everyone on server
			String text = command.substring(lengthToTrim);
			announceToAll(text);
		}
		
		// No body cares!
		catch (StringIndexOutOfBoundsException e)
		{
			// empty message.. ignore
		}
	}
	
	/**
	 * Announce to players.<BR>
	 * <BR>
	 * 
	 * @param message
	 *            The String of the message to send to player
	 */
	public void announceToPlayers(String message)
	{
		// Get all players
		for (L2Player player : L2World.getInstance().getAllPlayers())
			player.sendMessage(message);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final Announcements _instance = new Announcements();
	}
}
