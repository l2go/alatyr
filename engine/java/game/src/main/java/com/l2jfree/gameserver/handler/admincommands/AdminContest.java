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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;

/**
 * Quick control implementation.
 * @author Savormix
 */
public final class AdminContest implements IAdminCommandHandler
{
	private static final Logger _log = LoggerFactory.getLogger(AdminContest.class);
	private static final String[] COMMANDS = { "admin_contest_start", "admin_contest_cancel" };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		return COMMANDS;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#useAdminCommand(java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		command = st.nextToken();
		if (command.startsWith("admin_contest"))
		{
			if (st.countTokens() == 0)
			{
				activeChar.sendMessage("Specify clan hall ID (34 - Devastated castle, 64 - Fortress of the Dead)!");
				return false;
			}
			try
			{
				int hallId = Integer.parseInt(st.nextToken());
				if (command.endsWith("start"))
					ClanHallManager.getInstance().getClanHallById(hallId).getSiege().startSiege();
				else if (command.endsWith("cancel"))
					ClanHallManager.getInstance().getClanHallById(hallId).getSiege().endSiege(null);
				return true;
			}
			catch (NumberFormatException nfe)
			{
				activeChar.sendMessage("That's not a clan hall ID.");
			}
			catch (NullPointerException npe)
			{
				activeChar.sendMessage("That clan hall doesn't exist/is not contestable.");
			}
			catch (IndexOutOfBoundsException e)
			{
				_log.error("Caught it!", e);
			}
		}
		return false;
	}
}
