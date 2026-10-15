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

import java.util.Arrays;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.model.zone.form.Shape;

/**
 * @author NB4L1
 */
public abstract class L2MapRegion
{
	private final int[] _restarts = new int[Race.values().length];
	
	protected L2MapRegion(int restartId)
	{
		// add restartpoints by id
		Arrays.fill(_restarts, restartId);
	}
	
	protected final void setRestartId(Race race, int restartId)
	{
		_restarts[race.ordinal()] = restartId;
	}
	
	public final int getRestartId(L2Player player)
	{
		return getRestartId(player.getRace());
	}
	
	public final int getRestartId(Race race)
	{
		return _restarts[race.ordinal()];
	}
	
	public final int getRestartId()
	{
		return getRestartId(Race.Human);
	}
	
	public abstract boolean checkIfInRegion(int x, int y, int z);
	
	public final boolean intersectsRectangle(int ax1, int ax2, int ay1, int ay2)
	{
		return getShape().intersectsRectangle(ax1, ax2, ay1, ay2);
	}
	
	protected abstract Shape getShape();
}
