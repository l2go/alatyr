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
package com.l2jfree.gameserver.communitybbs;

import com.l2jfree.Config;
import com.l2jfree.gameserver.communitybbs.Manager.AuctionBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.ClanBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.DroplocatorBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.MailBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.PostBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.TopBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.TopicBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.UpdateBBSManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ShowBoard;

public class CommunityBoard
{
	public static void handleCommands(L2Client client, String command)
	{
		L2Player activeChar = client.getActiveChar();
		if (activeChar == null)
			return;
		
		switch (Config.COMMUNITY_TYPE)
		{
			default:
			case 0: // disabled
				activeChar.sendPacket(SystemMessageId.CB_OFFLINE);
				break;
			case 1: // old
				RegionBBSManager.getInstance().parsecmd(command, activeChar);
				break;
			case 2: // new
				if (command.startsWith("_bbsclan"))
				{
					ClanBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsmemo"))
				{
					TopicBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbstopics"))
				{
					TopicBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsposts"))
				{
					PostBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbstop"))
				{
					TopBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbshome"))
				{
					TopBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsloc"))
				{
					RegionBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_maillist_0_1_0_"))
				{
					MailBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsauction"))
				{
					AuctionBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsupdate"))
				{
					UpdateBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else if (command.startsWith("_bbsdroploc"))
				{
					DroplocatorBBSManager.getInstance().parsecmd(command, activeChar);
				}
				else
				{
					ShowBoard.notImplementedYet(activeChar, command);
				}
				break;
		}
	}
	
	/**
	 * @param client
	 * @param url
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @param arg5
	 */
	public static void handleWriteCommands(L2Client client, String url, String arg1, String arg2, String arg3,
			String arg4, String arg5)
	{
		L2Player activeChar = client.getActiveChar();
		if (activeChar == null)
			return;
		
		switch (Config.COMMUNITY_TYPE)
		{
			case 2:
				if (url.equals("Topic"))
				{
					TopicBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else if (url.equals("Post"))
				{
					PostBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else if (url.equals("Region"))
				{
					RegionBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else if (url.equals("Notice"))
				{
					ClanBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else if (url.equals("Mail"))
				{
					MailBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else if (url.equals("Auction"))
				{
					AuctionBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				}
				else
				{
					ShowBoard.notImplementedYet(activeChar, url);
				}
				break;
			case 1:
				RegionBBSManager.getInstance().parsewrite(arg1, arg2, arg3, arg4, arg5, activeChar);
				break;
			default:
			case 0:
				activeChar.sendPacket(SystemMessageId.CB_OFFLINE);
				break;
		}
	}
}
