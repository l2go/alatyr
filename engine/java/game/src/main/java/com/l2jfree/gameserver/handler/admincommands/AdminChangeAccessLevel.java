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
import java.sql.SQLException;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * This class handles following admin commands:
 * - changelvl = change a character's access level
 *  Can be used for character ban (as opposed to regular //ban that affects accounts)
 *  or to grant mod/GM privileges ingame
 * @version $Revision: 1.1.2.2.2.3 $ $Date: 2005/04/11 10:06:00 $
 */
public class AdminChangeAccessLevel implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_changelvl" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		handleChangeLevel(command, activeChar);
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	/**
	 * If no character name is specified, tries to change GM's target access level. Else
	 * if a character name is provided, will try to reach it either from L2World or from
	 * a database connection.
	 * 
	 * @param command
	 * @param activeChar
	 */
	private void handleChangeLevel(String command, L2Player activeChar)
	{
		String[] parts = command.split(" ");
		if (parts.length == 2)
		{
			try
			{
				int lvl = Integer.parseInt(parts[1]);
				if (activeChar.getTarget() instanceof L2Player)
					onLineChange(activeChar, (L2Player)activeChar.getTarget(), lvl);
				else
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //changelvl <target_new_level> | <player_name> <new_level>");
			}
		}
		else if (parts.length == 3)
		{
			String name = parts[1];
			int lvl = Integer.parseInt(parts[2]);
			L2Player player = L2World.getInstance().getPlayer(name);
			if (player != null)
				onLineChange(activeChar, player, lvl);
			else
			{
				Connection con = null;
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					PreparedStatement statement =
							con.prepareStatement("UPDATE player SET access_level=? WHERE name=?");
					statement.setInt(1, lvl);
					statement.setString(2, name);
					statement.execute();
					int count = statement.getUpdateCount();
					statement.close();
					if (count == 0)
						activeChar.sendMessage("Character not found or access level unaltered.");
					else
						activeChar.sendMessage("Character's access level is now set to " + lvl);
				}
				catch (SQLException se)
				{
					activeChar.sendMessage("SQLException while changing character's access level");
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
			}
		}
	}
	
	/**
	 * @param activeChar
	 * @param player
	 * @param lvl
	 */
	private void onLineChange(L2Player activeChar, L2Player player, int lvl)
	{
		player.setAccessLevel(lvl);
		if (lvl >= 0)
			player.sendMessage("Your access level has been changed to " + lvl);
		else
		{
			player.sendMessage("Your character has been banned. Bye.");
			new Disconnection(player).defaultSequence(false);
		}
		activeChar.sendMessage("Character's access level is now set to " + lvl
				+ ". Effects won't be noticeable until next session.");
	}
}
