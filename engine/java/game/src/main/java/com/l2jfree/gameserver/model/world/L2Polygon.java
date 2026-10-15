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
package com.l2jfree.gameserver.model.world;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author tReXpert
 */
public class L2Polygon implements Serializable
{
	private int _nPoints;
	private int _xPoints[];
	private int _yPoints[];
	
	private int _xMin = Integer.MAX_VALUE;
	private int _xMax = Integer.MIN_VALUE;
	private int _yMin = Integer.MAX_VALUE;
	private int _yMax = Integer.MIN_VALUE;
	
	private static final long serialVersionUID = -6460061437900069969L;
	
	public L2Polygon()
	{
		_xPoints = new int[3];
		_yPoints = new int[3];
	}
	
	public void addPoint(int x, int y)
	{
		if (_nPoints == _xPoints.length)
		{
			_xPoints = Arrays.copyOf(_xPoints, _nPoints + 1);
			_yPoints = Arrays.copyOf(_yPoints, _nPoints + 1);
		}
		
		_xPoints[_nPoints] = x;
		_yPoints[_nPoints] = y;
		
		_nPoints++;
		
		_xMin = Math.min(_xMin, x);
		_xMax = Math.max(_xMax, x);
		_yMin = Math.min(_yMin, y);
		_yMax = Math.max(_yMax, y);
	}
	
	public boolean contains(int x, int y)
	{
		if (x < _xMin || _xMax < x)
			return false;
		
		if (y < _yMin || _yMax < y)
			return false;
		
		//return contains((double) x, (double) y);
		return cn_PnPoly(x, y);
	}
	
	/**
	 * cn_PnPoly(): crossing number test for a point in a polygon
	 * Return:  false = outside, true = inside
	 * http://www.geometryalgorithms.com/Archive/algorithm_0103/algorithm_0103.htm
	 */
	private final boolean cn_PnPoly(int x, int y)
	{
		int cn = 0; // the crossing number counter
		
		// loop through all edges of the polygon
		for (int i = 0; i < _nPoints - 1; i++)
		{
			if (((_yPoints[i] <= y) && (_yPoints[i + 1] > y)) // an upward crossing
					|| ((_yPoints[i] > y) && (_yPoints[i + 1] <= y)))
			{ // a downward crossing
				// compute the actual edge-ray intersect x-coordinate
				float vt = (float)(y - _yPoints[i]) / (_yPoints[i + 1] - _yPoints[i]);
				if (x < _xPoints[i] + vt * (_xPoints[i + 1] - _xPoints[i])) // x < intersect
					++cn;
			}
		}
		return (cn & 1) == 1; // 0 if even (out), and 1 if odd (in)
	}
	
	public int[] getYPoints()
	{
		return _yPoints;
	}
	
	public int[] getXPoints()
	{
		return _xPoints;
	}
	
	public int size()
	{
		return _nPoints;
	}
}
