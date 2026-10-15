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

import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SiegeAttackerList;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestSiegeAttackerList extends L2ClientPacket
{
	private static final String _C__A2_RequestSiegeAttackerList = "[C] a2 RequestSiegeAttackerList";
	
	private int _siegeableID;
	
	@Override
	protected void readImpl()
	{
		_siegeableID = readD();
	}
	
	@Override
	protected void runImpl()
	{
		SiegeAttackerList sal = null;
		Castle castle = CastleManager.getInstance().getCastleById(_siegeableID);
		if (castle == null)
		{
			ClanHall hideout = ClanHallManager.getInstance().getClanHallById(_siegeableID);
			if (hideout != null && hideout.getSiege() != null)
				sal = new SiegeAttackerList(hideout);
		}
		else
			sal = new SiegeAttackerList(castle);
		if (sal != null)
			sendPacket(sal);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__A2_RequestSiegeAttackerList;
	}
}
