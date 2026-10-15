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
package com.l2jfree.gameserver.gameobjects.position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.taskmanager.CoordRevalidator;

public final class ObjectPosition
{
	private static final Logger _log = LoggerFactory.getLogger(ObjectPosition.class);
	
	private final L2Object _activeObject;
	
	private volatile L2WorldRegion _worldRegion;
	
	private volatile int _x;
	private volatile int _y;
	private volatile int _z;
	private volatile int _heading;
	
	public ObjectPosition(L2Object activeObject)
	{
		_activeObject = activeObject;
	}
	
	public L2WorldRegion getWorldRegion()
	{
		return _worldRegion;
	}
	
	public Location getCurrentLocation()
	{
		return new Location(_x, _y, _z, _heading);
	}
	
	public int getX()
	{
		return _x;
	}
	
	public int getY()
	{
		return _y;
	}
	
	public int getZ()
	{
		return _z;
	}
	
	public int getHeading()
	{
		return _heading;
	}
	
	public synchronized void setHeading(int value)
	{
		_heading = value;
	}
	
	public synchronized void setWorldPosition(int x, int y, int z)
	{
		if (x > L2World.MAP_MAX_X)
			x = L2World.MAP_MAX_X - 5000;
		if (x < L2World.MAP_MIN_X)
			x = L2World.MAP_MIN_X + 5000;
		if (y > L2World.MAP_MAX_Y)
			y = L2World.MAP_MAX_Y - 5000;
		if (y < L2World.MAP_MIN_Y)
			y = L2World.MAP_MIN_Y + 5000;
		
		_x = x;
		_y = y;
		_z = z;
	}
	
	public synchronized void setWorldPosition(ObjectPosition position)
	{
		synchronized (position)
		{
			setWorldPosition(position._x, position._y, position._z);
		}
	}
	
	public synchronized void setXYZInvisible(int x, int y, int z)
	{
		setWorldPosition(x, y, z);
		
		clearWorldRegion();
	}
	
	public synchronized void setXYZ(int x, int y, int z)
	{
		setWorldPosition(x, y, z);
		
		updateWorldRegion();
	}
	
	public synchronized void clearWorldRegion()
	{
		setWorldRegion(null);
	}
	
	public synchronized void updateWorldRegion()
	{
		try
		{
			setWorldRegion(L2World.getInstance().getRegion(_x, _y));
			
			CoordRevalidator.getInstance().add(_activeObject);
		}
		catch (RuntimeException e)
		{
			_log.warn(_activeObject + " at bad coords: (x: " + _x + ", y: " + _y + ", z: " + _z + ").", e);
			
			if (_activeObject instanceof L2Player)
			{
				((L2Player)_activeObject).teleToLocation(0, 0, 0, false);
				((L2Player)_activeObject).sendMessage("Error with your coords, Please ask a GM for help!");
			}
			else if (_activeObject instanceof L2Creature)
				_activeObject.decayMe();
		}
	}
	
	private void setWorldRegion(final L2WorldRegion newRegion)
	{
		final L2WorldRegion oldRegion = _worldRegion;
		
		if (oldRegion == newRegion)
			return;
		
		if (oldRegion != null)
		{
			oldRegion.removeVisibleObject(_activeObject, false);
			
			if (_activeObject instanceof L2Creature) // confirm revalidation of old region's zones
			{
				if (newRegion != null)
					oldRegion.revalidateZones((L2Creature)_activeObject); // at world region change
				else
					oldRegion.removeFromZones((L2Creature)_activeObject); // at world region change
			}
		}
		
		_worldRegion = newRegion;
		
		if (newRegion != null)
			newRegion.addVisibleObject(_activeObject, false);
	}
}
