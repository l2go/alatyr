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
package com.l2jfree.gameserver.communitybbs.Manager;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;

public class AdminBBSManager extends BaseBBSManager
{
	/**
	 * @return
	 */
	public static AdminBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsecmd(java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		if (activeChar.getAccessLevel() < Config.GM_ACCESSLEVEL)
		{
			return;
		}
		if (command.startsWith("admin_bbs"))
		{
			separateAndSend("<html><body><br><br><center>This Page is only an example :)<br><br>command=" + command
					+ "</center></body></html>", activeChar);
		}
		else
		{
			notImplementedYet(activeChar, command);
		}
	}
	
	/**
	 * @param activeChar
	 * @param file
	 */
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsewrite(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		if (activeChar.getAccessLevel() < Config.GM_ACCESSLEVEL)
			return;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AdminBBSManager _instance = new AdminBBSManager();
	}
}
