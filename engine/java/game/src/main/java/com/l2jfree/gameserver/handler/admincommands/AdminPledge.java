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

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.GMViewPledgeInfo;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * <B>Pledge Manipulation:</B><BR>
 * <LI>With target in a character without clan:<BR>
 * //pledge create clanname
 * <LI>With target in a clan leader:<BR>
 * //pledge info<BR>
 * //pledge dismiss<BR>
 * //pledge setlevel level<BR>
 * //pledge rep reputation_points<BR>
 */
public class AdminPledge implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_pledge" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return false;
		}
		String name = player.getName();
		if (command.startsWith("admin_pledge"))
		{
			String action = "";
			String parameter = null;
			StringTokenizer st = new StringTokenizer(command);
			try
			{
				st.nextToken();
				action = st.nextToken(); // create|info|dismiss|setlevel|rep
				parameter = st.nextToken(); // clanname|nothing|nothing|level|rep_points
			}
			catch (NoSuchElementException nse)
			{
			}
			if (action.equals("create"))
			{
				long cet = player.getClanCreateExpiryTime();
				player.setClanCreateExpiryTime(0);
				L2Clan clan = ClanTable.getInstance().createClan(player, parameter);
				if (clan != null)
					activeChar.sendMessage("Clan " + parameter + " created. Leader: " + name);
				else
				{
					player.setClanCreateExpiryTime(cet);
					activeChar.sendMessage("There was a problem while creating the clan.");
				}
			}
			else if (!player.isClanLeader())
			{
				activeChar.sendPacket(new SystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER).addString(name));
				return false;
			}
			else if (action.equals("dismiss"))
			{
				ClanTable.getInstance().destroyClan(player.getClanId());
				L2Clan clan = player.getClan();
				if (clan == null)
					activeChar.sendMessage("Clan disbanded.");
				else
					activeChar.sendMessage("There was a problem while destroying the clan.");
			}
			else if (parameter == null)
			{
				activeChar.sendMessage("Usage: //pledge <setlevel|rep> <number>");
			}
			else if (action.equals("info"))
			{
				activeChar.sendPacket(new GMViewPledgeInfo(player.getClan(), player));
			}
			else if (action.equals("setlevel"))
			{
				int level = 0;
				try
				{
					level = Integer.parseInt(parameter);
				}
				catch (NumberFormatException nfe)
				{
				}
				
				if (level >= 0 && level < 12)
				{
					player.getClan().changeLevel(level);
					activeChar.sendMessage("You set level " + level + " for clan " + player.getClan().getName());
				}
				else
					activeChar.sendMessage("Level incorrect.");
			}
			else if (action.startsWith("rep"))
			{
				int points = 0;
				try
				{
					points = Integer.parseInt(parameter);
				}
				catch (NumberFormatException nfe)
				{
					activeChar.sendMessage("Usage: //pledge <rep> <number>");
				}
				
				L2Clan clan = player.getClan();
				if (clan.getLevel() < 5)
				{
					activeChar.sendMessage("Only clans of level 5 or above may receive reputation points.");
					return false;
				}
				clan.setReputationScore(clan.getReputationScore() + points, true);
				activeChar.sendMessage("You " + (points > 0 ? "add " : "remove ") + Math.abs(points) + " points "
						+ (points > 0 ? "to " : "from ") + clan.getName() + "'s reputation. Their current score is "
						+ clan.getReputationScore());
			}
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
