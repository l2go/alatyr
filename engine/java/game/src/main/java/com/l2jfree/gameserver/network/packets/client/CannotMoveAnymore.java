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
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * This class represents a packet that is sent by the client once a character has been
 * running into a wall for a few moments and the client stopped the movement
 */
public final class CannotMoveAnymore extends L2ClientPacket
{
	private static final String _C__CANNOTMOVEANYMORE = "[C] 47 CannotMoveAnymore c[dddd]";
	
	private int _x;
	private int _y;
	private int _z;
	private int _heading;
	
	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_heading = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		if (_log.isDebugEnabled())
			_log.debug("client: x:" + _x + " y:" + _y + " z:" + _z + " server x:" + player.getX() + " y:"
					+ player.getY() + " z:" + player.getZ());
		
		player.getAI().notifyEvent(CtrlEvent.EVT_ARRIVED_BLOCKED, new L2CharPosition(_x, _y, _z, _heading));
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__CANNOTMOVEANYMORE;
	}
}
