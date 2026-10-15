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
import com.l2jfree.gameserver.instancemanager.TransformationManager;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author
 *
 */
public class AdminRide implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_ride_wyvern", "admin_ride_strider", "admin_ride_wolf",
			"admin_unride_wyvern", "admin_unride_strider", "admin_unride_wolf", "admin_ride_horse",
			"admin_unride_horse", "admin_unride", };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_ride"))
		{
			int petRideId;
			
			if (activeChar.isMounted() || activeChar.getPet() != null)
			{
				activeChar.sendMessage("Already have a pet or mounted.");
				return false;
			}
			if (command.startsWith("admin_ride_wyvern"))
			{
				petRideId = 12621;
			}
			else if (command.startsWith("admin_ride_strider"))
			{
				petRideId = 12526;
			}
			else if (command.startsWith("admin_ride_wolf"))
			{
				petRideId = 16041;
			}
			else if (command.startsWith("admin_ride_horse")) // handled using transformation
			{
				if (activeChar.getTransformation() != null)
					activeChar.sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_TRANSFORMED);
				else
					TransformationManager.getInstance().transformPlayer(106, activeChar);
				return true;
			}
			else
			{
				activeChar.sendMessage("Command '" + command + "' not recognized");
				return false;
			}
			activeChar.mount(petRideId, 0, false);
		}
		else if (command.startsWith("admin_unride"))
		{
			activeChar.dismount();
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
