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
package com.l2jfree.gameserver.geodata.pathfinding.geonodes;

import com.l2jfree.gameserver.geodata.pathfinding.Node;
import com.l2jfree.gameserver.model.world.L2World;

/**
 * @author NB4L1
 */
public final class GeoNode extends Node
{
	private final short _x;
	private final short _y;
	private final short _z;
	
	public GeoNode(short x, short y, short z, int neighborsIdx)
	{
		super(neighborsIdx);
		
		_x = x;
		_y = y;
		_z = z;
	}
	
	@Override
	public int getX()
	{
		return L2World.MAP_MIN_X + _x * 128 + 48;
	}
	
	@Override
	public int getY()
	{
		return L2World.MAP_MIN_Y + _y * 128 + 48;
	}
	
	@Override
	public short getZ()
	{
		return _z;
	}
	
	@Override
	public void setZ(short z)
	{
		//
	}
	
	@Override
	public int getNodeX()
	{
		return _x;
	}
	
	@Override
	public int getNodeY()
	{
		return _y;
	}
}
