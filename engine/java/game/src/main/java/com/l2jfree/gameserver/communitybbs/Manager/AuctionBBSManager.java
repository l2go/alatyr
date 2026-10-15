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

/**
 * The community board auction of player items. Its tables (auction_lots, auction_bids) have no home in the
 * Platform 3.0 schema, so the feature is gone: the board shows a notice and nothing is stored or processed.
 * 
 * @author Vital
 */
public class AuctionBBSManager extends BaseBBSManager
{
	public static AuctionBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		separateAndSend("<html><body><br><br><center>The auction board is not available on this server.</center><br><br></body></html>", activeChar);
	}
	
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		// The auction board is not available.
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AuctionBBSManager _instance = new AuctionBBSManager();
	}
}
