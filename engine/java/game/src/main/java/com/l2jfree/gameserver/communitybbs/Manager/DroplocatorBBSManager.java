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

import com.l2jfree.gameserver.gameobjects.L2Player;

public class DroplocatorBBSManager extends BaseBBSManager
{
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		if (command.equals("_bbsdroplocator_search"))
		{
			final StringBuilder content = new StringBuilder();
			content.append("<html><body><br>");
			content.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			content.append("<tr>");
			content.append("<td FIXWIDTH=5></td>");
			content.append("<td FIXWIDTH=150 align=center>Author</td>");
			content.append("<td FIXWIDTH=460 align=left>Title</td>");
			content.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			content.append("<td FIXWIDTH=5></td>");
			content.append("</tr></table>");
			content.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
			
			// FIXME: i guess something is missing from here :D
			content.toString();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsewrite
	 * (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String,
	 * com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		// TODO Auto-generated method stub
		
	}
	
	private static UpdateBBSManager _instance = new UpdateBBSManager();
	
	/**
	 * @return
	 */
	public static UpdateBBSManager getInstance()
	{
		return _instance;
	}
	
}
