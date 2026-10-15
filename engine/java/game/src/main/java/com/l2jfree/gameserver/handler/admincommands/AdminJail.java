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
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.zone.L2JailZone;

/**
 * This class handles following admin commands:
 * - jail charname [penalty_time] = jails character. Time specified in minutes. For ever if no time is specified.
 * - unjail charname = Unjails player, teleport him to Floran.
 * 
 * @version $Revision: 1.1.6.3 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminJail implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_jail", "admin_unjail" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		String player = "";
		
		if (command.startsWith("admin_jail"))
		{
			try
			{
				player = st.nextToken();
				int delay = 0;
				try
				{
					delay = Integer.parseInt(st.nextToken());
				}
				catch (Exception nfe)
				{
					activeChar.sendMessage("Usage: //jail <charname> [penalty_minutes]");
				}
				
				L2Player playerObj = L2World.getInstance().getPlayer(player);
				if (playerObj != null)
				{
					playerObj.setInJail(true, delay);
					activeChar.sendMessage("Character " + player + " jailed for "
							+ (delay > 0 ? delay + " minutes." : "ever!"));
				}
				else
					jailOfflinePlayer(activeChar, player, delay);
			}
			catch (NoSuchElementException nsee)
			{
				activeChar.sendMessage("Usage: //jail <charname> [penalty_minutes]");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else if (command.startsWith("admin_unjail"))
		{
			try
			{
				player = st.nextToken();
				L2Player playerObj = L2World.getInstance().getPlayer(player);
				
				if (playerObj != null)
				{
					playerObj.setInJail(false, 0);
					activeChar.sendMessage("Character " + player + " removed from jail");
				}
				else
					unjailOfflinePlayer(activeChar, player);
			}
			catch (NoSuchElementException nsee)
			{
				activeChar.sendMessage("Specify a character name.");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return true;
	}
	
	private void jailOfflinePlayer(L2Player activeChar, String name, int delay)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			
			PreparedStatement statement =
					con.prepareStatement("UPDATE player SET x=?, y=?, z=?, is_in_jail=?, jail_remaining_ms=? WHERE name=?");
			statement.setInt(1, L2JailZone.JAIL_LOCATION.getX());
			statement.setInt(2, L2JailZone.JAIL_LOCATION.getY());
			statement.setInt(3, L2JailZone.JAIL_LOCATION.getZ());
			statement.setBoolean(4, true);
			statement.setLong(5, Math.max(0, delay) * 60000L);
			statement.setString(6, name);
			
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			
			if (count == 0)
				activeChar.sendMessage("Character not found!");
			else
				activeChar.sendMessage("Character " + name + " jailed offline for"
						+ ((delay > 0) ? (" " + delay + " minutes.") : "ever!"));
		}
		catch (SQLException se)
		{
			activeChar.sendMessage("SQLException while jailing player");
			se.printStackTrace();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void unjailOfflinePlayer(L2Player activeChar, String name)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE player SET x=?, y=?, z=?, is_in_jail=?, jail_remaining_ms=? WHERE name=?");
			statement.setInt(1, 17836);
			statement.setInt(2, 170178);
			statement.setInt(3, -3507);
			statement.setBoolean(4, false);
			statement.setLong(5, 0);
			statement.setString(6, name);
			
			statement.execute();
			int count = statement.getUpdateCount();
			statement.close();
			if (count == 0)
				activeChar.sendMessage("Character not found!");
			else
				activeChar.sendMessage("Character " + name + " removed from jail while offline.");
		}
		catch (SQLException se)
		{
			activeChar.sendMessage("SQLException while unjailing player");
			se.printStackTrace();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
