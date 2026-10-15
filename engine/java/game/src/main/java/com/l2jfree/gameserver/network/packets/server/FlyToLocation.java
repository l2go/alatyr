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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author KenM
 */
public final class FlyToLocation extends L2ServerPacket
{
	private final int _objId;
	private final int _x, _y, _z;
	private final int _destX, _destY, _destZ;
	private final FlyType _type;
	
	public enum FlyType
	{
		THROW_UP,
		THROW_HORIZONTAL,
		DUMMY, // no effect
		CHARGE;
	}
	
	public FlyToLocation(L2Creature cha, int destX, int destY, int destZ, FlyType type)
	{
		_objId = cha.getObjectId();
		_x = cha.getX();
		_y = cha.getY();
		_z = cha.getZ();
		_destX = destX;
		_destY = destY;
		_destZ = destZ;
		_type = type;
	}
	
	public FlyToLocation(L2Creature cha, L2Creature dest, FlyType type)
	{
		this(cha, dest.getX(), dest.getY(), dest.getZ(), type);
	}
	
	@Override
	public String getType()
	{
		return "[S] 0xd4 FlyToLocation";
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xd4);
		writeD(_objId);
		writeD(_destX);
		writeD(_destY);
		writeD(_destZ);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_type.ordinal());
	}
}
