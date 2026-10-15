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
package com.l2jfree.gameserver.gameobjects.instance;

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;

public class L2FortDoormenInstance extends L2DoormenInstance
{
	public L2FortDoormenInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
	}
	
	@Override
	protected final void openDoors(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(10), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
		{
			getFort().openDoor(Integer.parseInt(st.nextToken()));
		}
	}
	
	@Override
	protected final void closeDoors(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(11), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
		{
			getFort().closeDoor(Integer.parseInt(st.nextToken()));
		}
	}
	
	@Override
	protected final boolean isOwnerClan(L2Player player)
	{
		if (player.isGM())
			return true;
		if (player.getClan() != null && getFort() != null && getFort().getOwnerClan() != null)
		{
			if (player.getClanId() == getFort().getOwnerClan().getClanId())
				return true;
		}
		return false;
	}
	
	// TODO: enable then teleports for forts will be done
	/*
	@Override
	protected final boolean isUnderSiege()
	{
		return getFort().getSiege().getIsInProgress();
	}
	*/
}
