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

public class ExJumpToLocation extends L2ServerPacket
{
	private static final String _S__EXJUMPTOLOCATION = "[S] FE:88 ExJumpToLocation ch[ddddddd]";
	
	private final int _objectId, _x, _y, _z;
	private final int _tx, _ty, _tz;
	
	public ExJumpToLocation(L2Player player, int tx, int ty, int tz)
	{
		_objectId = player.getObjectId();
		_x = player.getX();
		_y = player.getY();
		_z = player.getZ();
		_tx = player.getX();
		_ty = player.getY();
		_tz = player.getZ();
	}
	
	public ExJumpToLocation(L2Player player)
	{
		_objectId = player.getObjectId();
		_x = player.getX();
		_y = player.getY();
		_z = player.getZ();
		_tx = _x;
		_ty = _y;
		_tz = _z;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x88);
		
		writeD(_objectId);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_tx);
		writeD(_ty);
		writeD(_tz);
	}
	
	@Override
	public String getType()
	{
		return _S__EXJUMPTOLOCATION;
	}
}
