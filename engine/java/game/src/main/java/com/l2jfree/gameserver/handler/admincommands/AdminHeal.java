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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.world.L2World;

/**
 * This class handles following admin commands:
 * - heal = restores HP/MP/CP on target, name or radius
 * 
 * @version $Revision: 1.2.4.5 $ $Date: 2005/04/11 10:06:06 $
 */
public class AdminHeal implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_heal" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_heal"))
		{
			if (activeChar.getTarget() instanceof L2Creature)
			{
				handleHeal((L2Creature)activeChar.getTarget());
			}
		}
		else if (command.startsWith("admin_heal"))
		{
			try
			{
				String val = command.substring(11);
				
				try
				{
					int radius = Integer.parseInt(val);
					for (L2Creature cha : activeChar.getKnownList().getKnownCharactersInRadius(radius))
					{
						handleHeal(cha);
					}
				}
				catch (NumberFormatException e)
				{
					L2Player target = L2World.getInstance().getPlayer(val);
					
					if (target != null)
						handleHeal(target);
				}
			}
			catch (StringIndexOutOfBoundsException e)
			{
				activeChar.sendMessage("Incorrect target/radius specified.");
			}
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void handleHeal(L2Creature target)
	{
		target.getStatus().setCurrentHpMp(target.getMaxHp(), target.getMaxMp());
		if (target instanceof L2Player)
			target.getStatus().setCurrentCp(target.getMaxCp());
	}
}
