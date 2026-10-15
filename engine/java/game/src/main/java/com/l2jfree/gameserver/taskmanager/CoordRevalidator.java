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
package com.l2jfree.gameserver.taskmanager;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.network.packets.server.PartyMemberPosition;

/**
 * Used to revalidate/update/broadcast/execute tasks depending on current coordinates.<br>
 * <br>
 * The tasks gets triggered by the change of coordinates, but do not require instant execution.
 * 
 * @author NB4L1
 */
public final class CoordRevalidator extends AbstractFIFOPeriodicTaskManager<L2Object>
{
	private static final class SingletonHolder
	{
		private static final CoordRevalidator INSTANCE = new CoordRevalidator();
	}
	
	public static CoordRevalidator getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	protected CoordRevalidator()
	{
		super(500);
	}
	
	@Override
	protected void callTask(L2Object obj)
	{
		if (obj instanceof L2Creature && obj.isVisible())
		{
			final L2Creature cha = (L2Creature)obj;
			
			cha.getKnownList().updateKnownObjects();
			
			cha.revalidateZone(true);
			
			if (cha instanceof L2BoatInstance)
			{
				((L2BoatInstance)cha).updatePeopleInTheBoat(cha.getX(), cha.getY(), cha.getZ());
			}
			else if (cha instanceof L2AirShipInstance)
			{
				((L2AirShipInstance)cha).updatePeopleInTheAirShip(cha.getX(), cha.getY(), cha.getZ());
			}
			
			if (cha instanceof L2Player)
			{
				final L2Player player = (L2Player)cha;
				
				if (player.getParty() != null)
					player.getParty().broadcastToPartyMembers(player, new PartyMemberPosition(player));
			}
		}
	}
	
	@Override
	protected String getCalledMethodName()
	{
		return "revalidateCoords()";
	}
}
