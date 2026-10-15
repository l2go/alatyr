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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ShowMiniMap;

/**
 * format cd
 * 
 * @version $Revision: 1 $ $Date: 2005/04/10 00:17:44 $
 */
public final class RequestShowMiniMap extends L2ClientPacket
{
	private static final String _C__cd_REQUESTSHOWMINIMAP = "[C] cd RequestShowMiniMap";
	
	@Override
	protected void readImpl()
	{
		// trigger
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		sendPacket(new ShowMiniMap(1665));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__cd_REQUESTSHOWMINIMAP;
	}
}
