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

import org.w3c.dom.Node;

import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.model.world.L2Polygon;
import com.l2jfree.gameserver.model.zone.form.Shape;
import com.l2jfree.gameserver.model.zone.form.ShapePoly;

/**
 * @author Noctarius
 */
public final class L2SpecialMapRegion extends L2MapRegion
{
	private final L2Polygon _polygon = new L2Polygon();
	
	private final int _zMin;
	private final int _zMax;
	
	private final ShapePoly _shape;
	
	public L2SpecialMapRegion(Node node)
	{
		super(Integer.parseInt(node.getAttributes().getNamedItem("restartId").getNodeValue()));
		
		_zMin = Integer.parseInt(node.getAttributes().getNamedItem("zMin").getNodeValue());
		_zMax = Integer.parseInt(node.getAttributes().getNamedItem("zMax").getNodeValue());
		
		for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("point".equalsIgnoreCase(n.getNodeName()))
			{
				int X = Integer.parseInt(n.getAttributes().getNamedItem("X").getNodeValue());
				int Y = Integer.parseInt(n.getAttributes().getNamedItem("Y").getNodeValue());
				
				_polygon.addPoint(X, Y);
			}
			else if ("restart".equalsIgnoreCase(n.getNodeName()))
			{
				Race race = Race.getRaceByName(n.getAttributes().getNamedItem("race").getNodeValue());
				int restartId = Integer.parseInt(n.getAttributes().getNamedItem("restartId").getNodeValue());
				
				setRestartId(race, restartId);
			}
		}
		
		// add first point at the end of the polygon again for crossing
		_polygon.addPoint(_polygon.getXPoints()[0], _polygon.getYPoints()[0]);
		
		_shape = new ShapePoly(_polygon.getXPoints(), _polygon.getYPoints());
	}
	
	@Override
	public boolean checkIfInRegion(int x, int y, int z)
	{
		if (z != -1)
			if (z < _zMin || _zMax < z)
				return false;
		
		return _polygon.contains(x, y);
	}
	
	@Override
	protected Shape getShape()
	{
		return _shape;
	}
}
