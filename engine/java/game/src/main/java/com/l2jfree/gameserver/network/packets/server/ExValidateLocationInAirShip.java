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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @authos kerberos
 *
 */
public class ExValidateLocationInAirShip extends L2ServerPacket
{
	private final L2Player _activeChar;
	
	public ExValidateLocationInAirShip(L2Player player)
	{
		_activeChar = player;
	}
	
	@Override
	protected final void writeImpl()
	{
		if (_activeChar.getAirShip() == null)
			return;
		
		writeC(0xfe);
		writeH(0x6F);
		writeD(_activeChar.getObjectId());
		writeD(_activeChar.getAirShip().getObjectId());
		writeD(_activeChar.getX());
		writeD(_activeChar.getY());
		writeD(_activeChar.getZ());
		writeD(_activeChar.getHeading());
	}
	
	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "[S] FE:6F ExValidateLocationInAirShip";
	}
}
