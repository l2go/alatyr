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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * This class handles all siege commands:
 * Todo: change the class name, and neaten it up
 *
 *
 */
public class AdminFortSiege implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_fortsiege", "admin_add_fortattacker",
			"admin_list_fortsiege_clans", "admin_clear_fortsiege_list", "admin_spawn_fortdoors", "admin_endfortsiege",
			"admin_startfortsiege", "admin_setfort", "admin_removefort" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		command = st.nextToken(); // Get actual command
		
		// Get fort
		Fort fort = null;
		int fortId = 0;
		if (st.hasMoreTokens())
		{
			String val = st.nextToken();
			try
			{
				fortId = Integer.parseInt(val);
				fort = FortManager.getInstance().getFortById(fortId);
			}
			catch (Exception e)
			{
				fort = FortManager.getInstance().getFort(val);
			}
		}
		
		// Get fort
		if (fort == null)
		{
			// No valid fort specified
			showFortSelectPage(activeChar);
		}
		else
		{
			L2Object target = activeChar.getTarget();
			L2Player player = null;
			if (target instanceof L2Player)
				player = (L2Player)target;
			
			if (command.equalsIgnoreCase("admin_add_fortattacker"))
			{
				if (player == null)
					activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
				else
				{
					if (fort.getSiege().checkIfCanRegister(player))
						fort.getSiege().registerAttacker(player, true);
				}
			}
			//            else if (command.equalsIgnoreCase("admin_add_guard"))
			//            {
			//                try
			//                {
			//                    int npcId = Integer.parseInt(val);
			//                    fort.getSiege().getFortSiegeGuardManager().addFortSiegeGuard(activeChar, npcId);
			//                }
			//                catch (Exception e)
			//                {
			//                    activeChar.sendMessage("Usage: //add_guard npcId");
			//                }
			//            }
			else if (command.equalsIgnoreCase("admin_clear_fortsiege_list"))
			{
				fort.getSiege().clearSiegeClan();
			}
			else if (command.equalsIgnoreCase("admin_endfortsiege"))
			{
				fort.getSiege().endSiege();
			}
			else if (command.equalsIgnoreCase("admin_list_fortsiege_clans"))
			{
				activeChar.sendMessage("Not implemented yet.");
			}
			else if (command.equalsIgnoreCase("admin_setfort"))
			{
				if (player == null || player.getClan() == null)
					activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
				else
					fort.setOwner(player.getClan(), false);
			}
			else if (command.equalsIgnoreCase("admin_removefort"))
			{
				L2Clan clan = fort.getOwnerClan();
				if (clan != null)
					fort.removeOwner(true);
				else
					activeChar.sendMessage("Unable to remove fort");
			}
			else if (command.equalsIgnoreCase("admin_spawn_fortdoors"))
			{
				fort.resetDoors();
			}
			else if (command.equalsIgnoreCase("admin_startfortsiege"))
			{
				fort.getSiege().startSiege();
			}
			
			showFortSiegePage(activeChar, fort);
		}
		return true;
	}
	
	private void showFortSelectPage(L2Player activeChar)
	{
		int i = 0;
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/forts.htm");
		StringBuilder cList = new StringBuilder();
		for (Fort fort : FortManager.getInstance().getForts())
		{
			if (fort != null)
			{
				String name = fort.getName();
				cList.append("<td fixwidth=90><a action=\"bypass -h admin_fortsiege "
						+ String.valueOf(fort.getFortId()) + "\">" + name + " id: " + fort.getFortId() + "</a></td>");
				i++;
			}
			if (i > 2)
			{
				cList.append("</tr><tr>");
				i = 0;
			}
		}
		adminReply.replace("%forts%", cList.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private void showFortSiegePage(L2Player activeChar, Fort fort)
	{
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/fort.htm");
		adminReply.replace("%fortName%", fort.getName());
		adminReply.replace("%fortId%", fort.getFortId());
		activeChar.sendPacket(adminReply);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
