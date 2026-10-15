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
import com.l2jfree.gameserver.network.packets.server.ExListPartyMatchingWaitingRoom;

/**
 * Sent when a player opens the party matching window with waiting list or
 * clicks "List Update" (either waiting or room).
 * Format: (ch) dddd
 * @author Crion/kombat (format)
 * @author Myzreal (implementation)
 */
public class RequestListPartyMatchingWaitingRoom extends L2ClientPacket
{
	private static final String _C__D0_31_REQUESTLISTPARTYMATCHINGWAITINGROOM =
			"[C] D0:31 RequestListPartyMatchingWaitingRoom";
	
	private int _page;
	@SuppressWarnings("unused")
	private boolean _showAll;
	private int _minLevel;
	private int _maxLevel;
	
	@Override
	protected void readImpl()
	{
		_page = readD();
		_minLevel = readD();
		_maxLevel = readD();
		_showAll = readD() == 1; // client sends 0 if in party room, 1 if not in party room. If you are in party room, only players with matching level are shown.
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		// show all isn't used. TODO: test & compare
		sendPacket(new ExListPartyMatchingWaitingRoom(_minLevel, _maxLevel, _page));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_31_REQUESTLISTPARTYMATCHINGWAITINGROOM;
	}
}
