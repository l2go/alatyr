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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class ExMoveToLocationInAirShip extends L2ServerPacket
{
	private final L2Player _activeChar;
	private final L2CharPosition _destination;
	
	//private L2CharPosition _origin;
	/**
	 * @param actor
	 * @param destination
	 * @param origin
	 */
	public ExMoveToLocationInAirShip(L2Player player, L2CharPosition destination)
	{
		_activeChar = player;
		_destination = destination;
		//_origin = origin;
	}
	
	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		if (_activeChar.getAirShip() == null)
			return;
		
		writeC(0xfe);
		writeH(0x6D);
		writeD(_activeChar.getObjectId());
		writeD(_activeChar.getAirShip().getObjectId());
		writeD(_destination.x);
		writeD(_destination.y);
		writeD(_destination.z);
		writeD(_destination.heading);
	}
	
	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "[S] 6D MoveToLocationInAirShip";
	}
}
