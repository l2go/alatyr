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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.world.L2World;

/**
 * This class handles following admin commands:
 * - admin_banchat = Imposes a chat ban on the specified player/target.
 * - admin_unbanchat = Removes any chat ban on the specified player/target.
 * 
 * Uses:
 * admin_banchat [<player_name>] [<ban_duration>]
 * admin_unbanchat [<player_name>]
 * 
 * If <player_name> is not specified, the current target player is used.
 * 
 * @version $Revision: 1.1.6.3 $ $Date: 2005/04/11 10:06:06 $
 */
public final class AdminBanChat implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_banchat", "admin_unbanchat" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		L2Player targetPlayer = null;
		long banLength = -1;
		
		final String[] cmdParams = command.split(" ");
		if (cmdParams.length > 1)
		{
			targetPlayer = L2World.getInstance().getPlayer(cmdParams[1]);
			
			if (cmdParams.length > 2)
			{
				try
				{
					banLength = Integer.parseInt(cmdParams[2]);
				}
				catch (NumberFormatException nfe)
				{
				}
			}
		}
		else
		{
			targetPlayer = activeChar.getTarget(L2Player.class);
		}
		
		if (targetPlayer == null)
		{
			activeChar.sendMessage("Incorrect parameter or target.");
			return false;
		}
		
		if (command.startsWith("admin_banchat"))
		{
			ObjectRestrictions.getInstance().addRestriction(targetPlayer, AvailableRestriction.PlayerChat);
			
			if (banLength > -1)
			{
				ObjectRestrictions.getInstance().timedRemoveRestriction(targetPlayer.getObjectId(),
						AvailableRestriction.PlayerChat, banLength * 60000);
				
				targetPlayer.sendMessage("You have been chat banned by a server admin for " + banLength + " minutes.");
				activeChar.sendMessage(targetPlayer.getName() + " is now chat banned for " + banLength + " minutes.");
			}
			else
			{
				targetPlayer.sendMessage("You have been chat banned by a server admin forever.");
				activeChar.sendMessage(targetPlayer.getName() + " is now chat banned forever.");
			}
		}
		else if (command.startsWith("admin_unbanchat"))
		{
			ObjectRestrictions.getInstance().removeRestriction(targetPlayer, AvailableRestriction.PlayerChat);
			
			targetPlayer.sendMessage("You chat ban has now been lifted by a server admin.");
			activeChar.sendMessage(targetPlayer.getName() + "'s chat ban has now been lifted by a server admin.");
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
