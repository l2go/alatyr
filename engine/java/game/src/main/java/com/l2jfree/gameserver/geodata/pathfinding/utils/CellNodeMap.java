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
package com.l2jfree.gameserver.geodata.pathfinding.utils;

import com.l2jfree.gameserver.geodata.pathfinding.Node;
import com.l2jfree.util.L2FastSet;
import com.l2jfree.util.ObjectPool;

/**
 * @author Sami
 */
public final class CellNodeMap
{
	private final L2FastSet<Node> _cellIndex = new L2FastSet<Node>(4096);
	
	private CellNodeMap()
	{
	}
	
	public void add(Node n)
	{
		_cellIndex.add(n);
	}
	
	public boolean contains(Node n)
	{
		return _cellIndex.contains(n);
	}
	
	public static CellNodeMap newInstance()
	{
		return POOL.get();
	}
	
	public static void recycle(CellNodeMap map)
	{
		POOL.store(map);
	}
	
	private static final ObjectPool<CellNodeMap> POOL = new ObjectPool<CellNodeMap>() {
		@Override
		protected void reset(CellNodeMap map)
		{
			map._cellIndex.clear();
		}
		
		@Override
		protected CellNodeMap create()
		{
			return new CellNodeMap();
		}
	};
}
