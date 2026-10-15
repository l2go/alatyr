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

import com.l2jfree.Config;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.contract.ServerStatusAttributes;

/**
 * This class handles admin commands that manage the loginserver
 * @reworked by savormix
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2007/07/31 10:05:56 $
 */
public class AdminLogin implements IAdminCommandHandler
{
	private static final String[] LOGIN_COMMANDS = { "admin_login", "admin_login_conn", "admin_login_status",
			"admin_login_toggle", "admin_login_age" };
	
	private static final String HTML_ROOT = "data/html/admin/";
	
	private static final String ON = "ON";
	private static final String OFF = "OFF";
	private static final String ENABLE = "1";
	private static final String DISABLE = "0";
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#useAdminCommand(java.lang.String, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useAdminCommand(String command, L2Player GM)
	{
		StringTokenizer st;
		
		if (command.equalsIgnoreCase(LOGIN_COMMANDS[0]))
			showMenu(GM);
		else if (command.startsWith(LOGIN_COMMANDS[1]))
		{
			st = new StringTokenizer(command, " ");
			st.nextToken();
			int connCount;
			try
			{
				connCount = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showMenu(GM);
				return false;
			}
			LoginLink.getInstance().setMaxPlayers(connCount);
		}
		else if (command.startsWith(LOGIN_COMMANDS[2]))
		{
			st = new StringTokenizer(command, " ");
			st.nextToken();
			int newStatus;
			try
			{
				newStatus = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showMenu(GM);
				return false;
			}
			LoginLink.getInstance().setServerStatus(newStatus);
		}
		else if (command.startsWith(LOGIN_COMMANDS[3]))
		{
			st = new StringTokenizer(command, " ");
			st.nextToken();
			int attrib;
			int value;
			try
			{
				attrib = Integer.parseInt(st.nextToken());
				value = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showMenu(GM);
				return false;
			}
			LoginLink.getInstance().changeAttribute(attrib, value);
		}
		else if (command.startsWith(LOGIN_COMMANDS[4]))
		{
			st = new StringTokenizer(command, " ");
			st.nextToken();
			int age;
			try
			{
				age = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showMenu(GM);
				return false;
			}
			LoginLink.getInstance().changeAttribute(ServerStatusAttributes.SERVER_AGE_LIMITATION, age);
		}
		else
			return false;
		
		showMenu(GM);
		return true;
	}
	
	private void showMenu(L2Player GM)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile(HTML_ROOT + "LoginMenu.html");
		switch (LoginLink.getInstance().getServerStatus())
		{
			case STATUS_DOWN:
				html.replace("%statusCol%", "EE0000");
				html.replace("%statusStr%", OFF);
				break;
			case STATUS_GM_ONLY:
				html.replace("%statusCol%", "EEEE00");
				html.replace("%statusStr%", "MAINTENANCE");
				break;
			default:
				html.replace("%statusCol%", "00EE00");
				html.replace("%statusStr%", ON);
				break;
		}
		html.replace("%statusB1%", Config.SERVER_BIT_1 ? ON : OFF);
		html.replace("%statusB2%", Config.SERVER_LIST_CLOCK ? ON : OFF);
		html.replace("%statusB3%", Config.SERVER_BIT_3 ? ON : OFF);
		html.replace("%statusB4%", Config.SERVER_LIST_TESTSERVER ? ON : OFF);
		html.replace("%b1%", Config.SERVER_BIT_1 ? DISABLE : ENABLE);
		html.replace("%b2%", Config.SERVER_LIST_CLOCK ? DISABLE : ENABLE);
		html.replace("%b3%", Config.SERVER_BIT_3 ? DISABLE : ENABLE);
		html.replace("%b4%", Config.SERVER_LIST_TESTSERVER ? DISABLE : ENABLE);
		html.replace("%statusBr%", Config.SERVER_LIST_BRACKET ? ON : OFF);
		html.replace("%br%", Config.SERVER_LIST_BRACKET ? DISABLE : ENABLE);
		html.replace("%maxConn%", String.valueOf(LoginLink.getInstance().getMaxPlayer()));
		GM.sendPacket(html);
		html = null;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		return LOGIN_COMMANDS;
	}
}
