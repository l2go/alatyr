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
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.model.entity.Instance;

/**
 * @author evill33t
 * 
 */
public class AdminInstance implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_setinstance", "admin_createinstance",
			"admin_destroyinstance", "admin_listinstances" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		// create new instance
		if (command.startsWith("admin_createinstance"))
		{
			String[] parts = command.split(" ");
			if (parts.length < 1)
			{
				activeChar.sendMessage("Example: //createinstance <templatefile>");
			}
			else
			{
				try
				{
					int id = InstanceManager.getInstance().createDynamicInstance(parts[1]);
					activeChar.sendMessage("Instance created: " + id);
					return true;
				}
				catch (Exception e)
				{
					activeChar.sendMessage("Invalid request or missing file.");
					return false;
				}
			}
		}
		else if (command.startsWith("admin_listinstances"))
		{
			for (Instance temp : InstanceManager.getInstance().getInstances().values())
			{
				activeChar.sendMessage("Id: " + temp.getId() + " Name: " + temp.getName());
			}
		}
		else if (command.startsWith("admin_setinstance"))
		{
			try
			{
				int val = Integer.parseInt(st.nextToken());
				if (InstanceManager.getInstance().getInstance(val) == null)
				{
					activeChar.sendMessage("Instance " + val + " doesnt exist.");
					return false;
				}
				
				L2Object target = activeChar.getTarget();
				if (target == null || target instanceof L2Summon) // Don't separate summons from masters
				{
					activeChar.sendMessage("Incorrect target.");
					return false;
				}
				target.setInstanceId(val);
				if (target instanceof L2Player)
				{
					L2Player player = (L2Player)target;
					player.sendMessage("Admin set your instance to:" + val);
					InstanceManager.getInstance().getInstance(val).addPlayer(player.getObjectId());
					player.teleToLocation(player.getX(), player.getY(), player.getZ());
					L2Summon pet = player.getPet();
					if (pet != null)
					{
						pet.teleToLocation(pet.getX(), pet.getY(), pet.getZ());
						player.sendMessage("Admin set " + pet.getName() + "'s instance to:" + val);
					}
				}
				activeChar.sendMessage("Moved " + target.getName() + " to instance " + target.getInstanceId() + ".");
				return true;
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Use //setinstance id");
			}
		}
		else if (command.startsWith("admin_destroyinstance"))
		{
			try
			{
				int val = Integer.parseInt(st.nextToken());
				InstanceManager.getInstance().destroyInstance(val);
				activeChar.sendMessage("Instance destroyed");
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Use //destroyinstance id");
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
