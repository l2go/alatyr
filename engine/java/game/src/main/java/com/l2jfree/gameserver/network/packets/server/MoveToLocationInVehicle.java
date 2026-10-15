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

/**
 * @author Maktakien
 *
 */
public class MoveToLocationInVehicle extends L2ServerPacket
{
	private final L2Player _activeChar;
	private final L2CharPosition _destination;
	private final L2CharPosition _origin;
	
	/**
	 * @param actor
	 * @param destination
	 * @param origin
	 */
	public MoveToLocationInVehicle(L2Player player, L2CharPosition destination, L2CharPosition origin)
	{
		_activeChar = player;
		_destination = destination;
		_origin = origin;
		/*	_pci.sendMessage("_destination : x " + x +" y " + y + " z " + z);
			_pci.sendMessage("_boat : x " + _pci.getBoat().getX() +" y " + _pci.getBoat().getY() + " z " + _pci.getBoat().getZ());
			_pci.sendMessage("-----------");*/
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		if (_activeChar.getBoat() == null)
			return;
		
		writeC(0x7e);
		writeD(_activeChar.getObjectId());
		writeD(_activeChar.getBoat().getObjectId());
		writeD(_destination.x);
		writeD(_destination.y);
		writeD(_destination.z);
		writeD(_origin.x);
		writeD(_origin.y);
		writeD(_origin.z);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return "[S] 71 MoveToLocationInVehicle";
	}
}
