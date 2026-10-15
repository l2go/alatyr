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
public class DoorInfo extends L2ServerPacket
{
	private static final String _S__4C_DOORINFO = "[S] 4c DoorInfo [ddd]";
	private final L2DoorInstance _door;
	
	public DoorInfo(L2DoorInstance door)
	{
		_door = door;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x4c);
		writeD(_door.getObjectId());
		writeD(_door.getDoorId());
		writeD(0x00); //door hp view
	}
	
	@Override
	public String getType()
	{
		return _S__4C_DOORINFO;
	}
}
