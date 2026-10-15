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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;

public class AdminSendHome implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_sendhome" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_sendhome"))
		{
			L2Player targetPlayer;
			if (command.split(" ").length > 1)
			{
				targetPlayer = L2World.getInstance().getPlayer(command.split(" ")[1]);
			}
			else
			{
				L2Object target = activeChar.getTarget();
				if (target == null)
					targetPlayer = activeChar;
				else
					targetPlayer = L2Object.getActingPlayer(target);
			}
			
			if (targetPlayer != null)
				targetPlayer.teleToLocation(TeleportWhereType.Town);
			else
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
