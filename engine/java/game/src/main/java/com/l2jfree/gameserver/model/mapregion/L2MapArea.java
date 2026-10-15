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
package com.l2jfree.gameserver.model.mapregion;

import com.l2jfree.gameserver.model.zone.form.Shape;
import com.l2jfree.gameserver.model.zone.form.ShapeRect;

/**
 * @author Noctarius
 */
public final class L2MapArea extends L2MapRegion
{
	private static final int AREA_WIDTH = 32768;
	
	private final int _xMin;
	private final int _xMax;
	
	private final int _yMin;
	private final int _yMax;
	
	private final ShapeRect _shape;
	
	public L2MapArea(int restartId, int x, int y)
	{
		super(restartId);
		
		_xMin = (x - 15) * AREA_WIDTH - 164608;
		_xMax = _xMin + AREA_WIDTH;
		
		_yMin = (y - 10) * AREA_WIDTH - 262144;
		_yMax = _yMin + AREA_WIDTH;
		
		_shape = new ShapeRect(_xMin, _xMax, _yMin, _yMax);
	}
	
	@Override
	public boolean checkIfInRegion(int x, int y, int z)
	{
		return _xMin <= x && x <= _xMax && _yMin <= y && y <= _yMax;
	}
	
	@Override
	protected Shape getShape()
	{
		return _shape;
	}
}
