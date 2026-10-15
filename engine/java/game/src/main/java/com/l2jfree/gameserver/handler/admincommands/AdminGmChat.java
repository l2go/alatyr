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

import java.util.StringTokenizer;

import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;

/**
 * This class handles following admin commands:
 * - gmchat text = sends text to all online GM's
 * - gmchat_menu text = same as gmchat, displays the admin panel after chat
 * 
 * @version $Revision: 1.2.4.3 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminGmChat implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_gmchat", "admin_snoop", "admin_gmchat_menu" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_gmchat"))
			handleGmChat(command, activeChar);
		else if (command.startsWith("admin_snoop"))
			snoop(command, activeChar);
		if (command.startsWith("admin_gmchat_menu"))
			AdminHelpPage.showHelpPage(activeChar, "main_menu.htm");
		return true;
	}
	
	/**
	 * @param command
	 * @param activeChar
	 */
	private void snoop(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (!st.hasMoreTokens())
		{
			activeChar.sendMessage("Usage: //snoop <player_name>");
			return;
		}
		
		L2Player player = L2World.getInstance().getPlayer(st.nextToken());
		if (player == null)
		{
			activeChar.sendPacket(SystemMessageId.TARGET_CANT_FOUND);
			return;
		}
		if (player.getAccessLevel() > activeChar.getAccessLevel())
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			player.sendMessage(activeChar.getName() + " tried to snoop your conversations. Blocked.");
			return;
		}
		player.addSnooper(activeChar); // GM added to player list
		activeChar.addSnooped(player); // Player added to GM list
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	/**
	 * @param command
	 * @param activeChar
	 */
	private void handleGmChat(String command, L2Player activeChar)
	{
		try
		{
			int offset = 0;
			String text;
			if (command.startsWith("admin_gmchat_menu"))
				offset = 18;
			else
				offset = 13;
			text = command.substring(offset);
			CreatureSay cs = new CreatureSay(0, SystemChatChannelId.Chat_Alliance, activeChar.getName(), text);
			GmListTable.broadcastToGMs(cs);
		}
		catch (StringIndexOutOfBoundsException e)
		{
			// empty message.. ignore
		}
	}
}
