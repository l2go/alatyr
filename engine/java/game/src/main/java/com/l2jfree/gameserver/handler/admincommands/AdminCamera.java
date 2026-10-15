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
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author Made in Taiwan
 */
public class AdminCamera implements IAdminCommandHandler
{
	
	private static final String[] ADMIN_COMMANDS = { "admin_camera", "admin_camset", };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_camera"))
			AdminHelpPage.showHelpPage(activeChar, "camera_menu.htm");
		
		else if (command.startsWith("admin_camset"))
		{
			if (activeChar.getTarget() == null)
			{
				activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
			}
			else
			{
				StringTokenizer st = new StringTokenizer(command);
				st.nextToken();
				
				try
				{
					L2Object target = activeChar.getTarget();
					int scDist = Integer.parseInt(st.nextToken());
					int scYaw = Integer.parseInt(st.nextToken());
					int scPitch = Integer.parseInt(st.nextToken());
					int scTime = Integer.parseInt(st.nextToken());
					int scDuration = Integer.parseInt(st.nextToken());
					activeChar.sendMessage("camera " + scDist + "," + scYaw + "," + scPitch + "," + scTime + ","
							+ scDuration);
					activeChar.enterMovieMode();
					activeChar.specialCamera(target, scDist, scYaw, scPitch, scTime, scDuration);
				}
				catch (Exception e)
				{
					activeChar.sendMessage("usage: //camera dist yaw pitch time duration");
				}
				finally
				{
					activeChar.leaveMovieMode();
				}
				
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
