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
package com.l2jfree.gameserver.model.olympiad;

import java.util.Set;

import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.util.L2FastSet;

/**
 * @author GodKratos
 */
public final class OlympiadStadium
{
	private boolean _freeToUse = true;
	private final int[] _doorIds = new int[2];
	private final Set<L2Player> _spectators = new L2FastSet<L2Player>().setShared(true);
	public final Location player1Spawn;
	public final Location player2Spawn;
	public final Location buffer1Spawn;
	public final Location buffer2Spawn;
	
	public boolean isFreeToUse()
	{
		return _freeToUse;
	}
	
	public void setStadiaBusy()
	{
		_freeToUse = false;
	}
	
	public void setStadiaFree()
	{
		_freeToUse = true;
	}
	
	public OlympiadStadium(int x, int y, int z, int d1, int d2)
	{
		_doorIds[0] = d1;
		_doorIds[1] = d2;
		
		player1Spawn = new Location(x + 1200, y, z);
		player2Spawn = new Location(x - 1200, y, z);
		buffer1Spawn = new Location(x + 1100, y, z);
		buffer2Spawn = new Location(x - 1100, y, z);
	}
	
	public void openDoors()
	{
		DoorTable.getInstance().openDoors(_doorIds);
	}
	
	public void closeDoors()
	{
		DoorTable.getInstance().closeDoors(_doorIds);
	}
	
	protected void addSpectator(int id, L2Player spec, boolean storeCoords)
	{
		spec.enterOlympiadObserverMode(player1Spawn, id, storeCoords);
		
		_spectators.add(spec);
	}
	
	protected Set<L2Player> getSpectators()
	{
		return _spectators;
	}
	
	protected void removeSpectator(L2Player spec)
	{
		_spectators.remove(spec);
	}
}
