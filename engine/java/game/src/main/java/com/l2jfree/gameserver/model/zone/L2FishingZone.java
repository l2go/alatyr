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
package com.l2jfree.gameserver.model.zone;

import com.l2jfree.gameserver.gameobjects.L2Creature;

public class L2FishingZone extends L2Zone
{
	@Override
	protected void onEnter(L2Creature character)
	{
		character.setInsideZone(FLAG_FISHING, true);
		
		super.onEnter(character);
	}
	
	@Override
	protected void onExit(L2Creature character)
	{
		character.setInsideZone(FLAG_FISHING, false);
		
		super.onExit(character);
	}
}
