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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * This class handles following admin commands:
 * - handles every admin menu command
 * 
 * @version $Revision: 1.3.2.6.2.4 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminMenu implements IAdminCommandHandler
{
	private static final Logger _log = LoggerFactory.getLogger(AdminMenu.class);
	
	private static final String[] ADMIN_COMMANDS = { "admin_char_manage", "admin_teleport_character_to_menu",
			"admin_recall_char", "admin_recall_char_menu", "admin_recall_party", "admin_recall_party_menu",
			"admin_recall_clan", "admin_recall_clan_menu", "admin_goto_char_menu", "admin_kick_menu",
			"admin_kill_menu", "admin_ban_menu", "admin_unban_menu" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (command.equals("admin_char_manage"))
			showMainPage(activeChar);
		else if (command.startsWith("admin_teleport_character_to_menu"))
		{
			String[] data = command.split(" ");
			if (data.length == 5)
			{
				String playerName = data[1];
				L2Player player = L2World.getInstance().getPlayer(playerName);
				if (player != null)
					teleportCharacter(player, Integer.parseInt(data[2]), Integer.parseInt(data[3]),
							Integer.parseInt(data[4]), activeChar, "Admin is teleporting you.");
			}
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_recall_char"))
		{
			try
			{
				String targetName = st.nextToken();
				L2Player player = L2World.getInstance().getPlayer(targetName);
				teleportCharacter(player, activeChar.getX(), activeChar.getY(), activeChar.getZ(), activeChar,
						"Admin is teleporting you.");
			}
			catch (Exception e)
			{
			}
		}
		else if (command.startsWith("admin_recall_party"))
		{
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			try
			{
				String targetName = st.nextToken();
				L2Player player = L2World.getInstance().getPlayer(targetName);
				if (player == null)
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return true;
				}
				if (!player.isInParty())
				{
					activeChar.sendMessage("Player is not in party.");
					teleportCharacter(player, x, y, z, activeChar, "Admin is teleporting you.");
					return true;
				}
				for (L2Player pm : player.getParty().getPartyMembers())
					teleportCharacter(pm, x, y, z, activeChar, "Your party is being teleported by an Admin.");
			}
			catch (Exception e)
			{
			}
		}
		else if (command.startsWith("admin_recall_clan"))
		{
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			try
			{
				String targetName = st.nextToken();
				L2Player player = L2World.getInstance().getPlayer(targetName);
				if (player == null)
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
					return true;
				}
				L2Clan clan = player.getClan();
				if (clan == null)
				{
					activeChar.sendMessage("Player is not in a clan.");
					teleportCharacter(player, x, y, z, activeChar, "Admin is teleporting you.");
					return true;
				}
				L2Player[] members = clan.getOnlineMembers(0);
				for (L2Player element : members)
					teleportCharacter(element, x, y, z, activeChar, "Your clan is being teleported by an Admin.");
			}
			catch (Exception e)
			{
			}
		}
		else if (command.startsWith("admin_goto_char_menu"))
		{
			try
			{
				String targetName = st.nextToken();
				L2Player player = L2World.getInstance().getPlayer(targetName);
				teleportToCharacter(activeChar, player);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Target not found.");
				showMainPage(activeChar);
			}
		}
		else if (command.equals("admin_kill_menu"))
		{
			handleKill(activeChar);
		}
		else if (command.startsWith("admin_kick_menu"))
		{
			if (st.hasMoreTokens())
			{
				String player = st.nextToken();
				L2Player plyr = L2World.getInstance().getPlayer(player);
				if (plyr != null)
				{
					new Disconnection(plyr).defaultSequence(false);
					activeChar.sendMessage("You kicked " + plyr.getName() + " from the game.");
				}
				else
					activeChar.sendMessage("Player " + player + " was not found in the game.");
			}
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_ban_menu"))
		{
			if (st.hasMoreTokens())
			{
				String player = st.nextToken();
				L2Player plyr = L2World.getInstance().getPlayer(player);
				if (plyr != null)
				{
					new Disconnection(plyr).defaultSequence(false);
				}
				setAccountAccessLevel(player, activeChar, -100);
			}
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_unban_menu"))
		{
			if (st.hasMoreTokens())
			{
				String player = st.nextToken();
				setAccountAccessLevel(player, activeChar, 0);
			}
			showMainPage(activeChar);
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void handleKill(L2Player activeChar)
	{
		handleKill(activeChar, null);
	}
	
	private void handleKill(L2Player activeChar, String player)
	{
		L2Object obj = activeChar.getTarget();
		L2Creature target = (L2Creature)obj;
		String filename = "main_menu.htm";
		if (player != null)
		{
			L2Player plyr = L2World.getInstance().getPlayer(player);
			if (plyr != null)
			{
				target = plyr;
				activeChar.sendMessage("You killed " + plyr.getName());
			}
		}
		if (target != null)
		{
			if (target instanceof L2Player)
			{
				target.reduceCurrentHp(target.getMaxHp() + target.getMaxCp() + 1, activeChar);
				filename = "charmanage.htm";
			}
			else if (target.isChampion())
				target.reduceCurrentHp(target.getMaxHp() * Config.CHAMPION_HP + 1, activeChar);
			else
				target.reduceCurrentHp(target.getMaxHp() + 1, activeChar);
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
		}
		AdminHelpPage.showHelpPage(activeChar, filename);
	}
	
	private void teleportCharacter(L2Player player, int x, int y, int z, L2Player activeChar, String message)
	{
		if (player != null)
		{
			player.sendMessage(message);
			player.teleToLocation(x, y, z, true);
		}
		else
			activeChar.sendMessage("Target not found.");
		showMainPage(activeChar);
	}
	
	private void teleportToCharacter(L2Player activeChar, L2Object target)
	{
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		if (player == activeChar)
			player.sendPacket(SystemMessageId.CANNOT_USE_ON_YOURSELF);
		else
		{
			activeChar.teleToLocation(player.getX(), player.getY(), player.getZ(), true);
			activeChar.sendMessage("You're teleporting yourself to character " + player.getName());
		}
		showMainPage(activeChar);
	}
	
	/**
	 * @param activeChar
	 */
	private void showMainPage(L2Player activeChar)
	{
		AdminHelpPage.showHelpPage(activeChar, "charmanage.htm");
	}
	
	private void setAccountAccessLevel(String player, L2Player activeChar, int banLevel)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			String stmt = "SELECT account_name FROM player WHERE name = ?";
			PreparedStatement statement = con.prepareStatement(stmt);
			statement.setString(1, player);
			ResultSet result = statement.executeQuery();
			if (result.next())
			{
				String acc_name = result.getString(1);
				if (acc_name.length() > 0)
				{
					LoginLink.getInstance().sendAccessLevel(acc_name, banLevel);
					activeChar.sendMessage("Account Access Level for " + player + " set to " + banLevel + ".");
				}
				else
					activeChar.sendMessage("Couldn't find player: " + player + ".");
			}
			else
				activeChar.sendMessage("Specified player name didn't lead to a valid account.");
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Could not set accessLevel:", e);
			if (_log.isDebugEnabled())
				e.printStackTrace();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
}
