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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;

public class AdminKick implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_kick", "admin_kick_non_gm" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_kick"))
		{
			StringTokenizer st = new StringTokenizer(command);
			if (st.countTokens() > 1)
			{
				st.nextToken();
				String plyr = st.nextToken();
				L2Player player = L2World.getInstance().getPlayer(plyr);
				if (player != null)
				{
					new Disconnection(player).defaultSequence(false);
					activeChar.sendMessage("You kicked " + player.getName() + " from the game.");
				}
			}
		}
		if (command.startsWith("admin_kick_non_gm"))
		{
			int counter = 0;
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				if (!player.isGM())
				{
					counter++;
					new Disconnection(player).defaultSequence(false);
				}
			}
			activeChar.sendMessage("Kicked " + counter + " players");
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
