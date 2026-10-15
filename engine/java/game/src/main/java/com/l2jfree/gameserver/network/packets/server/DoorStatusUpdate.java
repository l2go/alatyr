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

import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/** @deprecated Seems to be completely replaced by StaticObject in retail */
@Deprecated
public class DoorStatusUpdate extends L2ServerPacket
{
	private static final String _S__4d_DOORSTATUSUPDATE = "[S] 4d DoorStatusUpdate [ddddddd]";
	private final L2DoorInstance _door;
	
	public DoorStatusUpdate(L2DoorInstance door)
	{
		_door = door;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x4d);
		writeD(_door.getObjectId());
		writeD(_door.isOpen() ? 0 : 1);
		writeD(_door.getDamageGrade());
		writeD(_door.isEnemy() ? 1 : 0);
		writeD(_door.getDoorId());
		writeD((int)_door.getStatus().getCurrentHp());
		writeD(_door.getMaxHp());
	}
	
	@Override
	public String getType()
	{
		return _S__4d_DOORSTATUSUPDATE;
	}
}
