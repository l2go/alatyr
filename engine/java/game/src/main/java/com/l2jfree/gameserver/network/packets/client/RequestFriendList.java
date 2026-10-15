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

import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public final class RequestFriendList extends L2ClientPacket
{
	private static final String _C__60_REQUESTFRIENDLIST = "[C] 60 RequestFriendList";
	
	/**
	 * packet type id 0x60
	 * format: c
	 */
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		sendPacket(SystemMessageId.FRIEND_LIST_HEADER);
		
		SystemMessage sm;
		for (Integer objId : activeChar.getFriendList().getFriendIds())
		{
			L2Player friend = L2World.getInstance().findPlayer(objId);
			if (friend == null)
			{
				sm = new SystemMessage(SystemMessageId.S1_OFFLINE);
				sm.addString(CharNameTable.getInstance().getByObjectId(objId));
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.S1_ONLINE);
				sm.addPcName(friend);
			}
			sendPacket(sm);
		}
		
		sendPacket(SystemMessageId.FRIEND_LIST_FOOTER);
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__60_REQUESTFRIENDLIST;
	}
}
