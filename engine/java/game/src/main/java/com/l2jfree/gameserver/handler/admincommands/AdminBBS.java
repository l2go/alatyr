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

import com.l2jfree.gameserver.communitybbs.Manager.AdminBBSManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;

public class AdminBBS implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_bbs" };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#useAdminCommand(java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		AdminBBSManager.getInstance().parsecmd(command, activeChar);
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
