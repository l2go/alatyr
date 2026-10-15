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
package com.l2jfree.gameserver.model;


import java.util.ArrayList;
import java.util.List;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.server.RadarControl;

public final class L2Marker
{
	private final L2Player _player;
	private final List<MapMarker> _markers;
	
	public L2Marker(L2Player player)
	{
		_player = player;
		_markers = new ArrayList<MapMarker>();
	}
	
	/**
	 * Adds a red flag to the world map (mini-map) at given coordinates.
	 * Doesn't check if a flag is already present.
	 * @param x
	 * @param y
	 * @param z
	 */
	public void addMarker(int x, int y, int z)
	{
		_markers.add(new MapMarker(x, y, z));
		_player.sendPacket(new RadarControl(RadarControl.MARKER_ADD, RadarControl.FLAG_1, x, y, z));
	}
	
	/**
	 * Removes a red flag from the world map (mini-map) at given coordinates if it's present.
	 * @param x
	 * @param y
	 * @param z
	 */
	public void removeMarker(int x, int y, int z)
	{
		MapMarker newMarker = new MapMarker(x, y, z);
		_markers.remove(newMarker);
		_player.sendPacket(new RadarControl(RadarControl.MARKER_REMOVE, RadarControl.FLAG_1, x, y, z));
	}
	
	public void removeAllMarkers()
	{
		_markers.clear();
		_player.sendPacket(RadarControl.REMOVE_ALL);
	}
	
	public void loadMarkers()
	{
		_player.sendPacket(RadarControl.REMOVE_ALL);
		for (MapMarker mark : _markers)
			_player.sendPacket(new RadarControl(RadarControl.MARKER_ADD, RadarControl.FLAG_1, mark._x, mark._y, mark._z));
	}
	
	public static class MapMarker
	{
		// Simple class to model radar points.
		public int _type, _x, _y, _z;
		
		public MapMarker(int type, int x, int y, int z)
		{
			_type = type;
			_x = x;
			_y = y;
			_z = z;
		}
		
		public MapMarker(int x, int y, int z)
		{
			_type = RadarControl.FLAG_1;
			_x = x;
			_y = y;
			_z = z;
		}
		
		/**
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + _type;
			result = prime * result + _x;
			result = prime * result + _y;
			result = prime * result + _z;
			return result;
		}
		
		/**
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof MapMarker))
				return false;
			final MapMarker other = (MapMarker)obj;
			if (_type != other._type)
				return false;
			if (_x != other._x)
				return false;
			if (_y != other._y)
				return false;
			if (_z != other._z)
				return false;
			return true;
		}
	}
}
