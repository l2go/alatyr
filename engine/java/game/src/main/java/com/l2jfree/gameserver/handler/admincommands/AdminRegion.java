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
package com.l2jfree.gameserver.handler.admincommands;

/**
 * 
 * @author luisantonioa
 * 
 */

import java.util.StringTokenizer;

import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.mapregion.L2MapRegion;
import com.l2jfree.gameserver.model.mapregion.L2MapRegionRestart;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;

/**
 * @author Noctarius
 *
 */
public class AdminRegion implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_region_check" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		
		if (actualCommand.equalsIgnoreCase("admin_region_check"))
		{
			L2MapRegion region = MapRegionManager.getInstance().getRegion(activeChar);
			
			if (region != null)
			{
				L2MapRegionRestart restart =
						MapRegionManager.getInstance().getRestartLocation(region.getRestartId(activeChar));
				
				//activeChar.sendMessage("Actual region: " + region.getId());
				activeChar.sendMessage("Respawn position will be: " + restart.getName() + " (" + restart.getLocName()
						+ ")");
				
				if (restart.getBannedRace() != null)
				{
					L2MapRegionRestart redirect =
							MapRegionManager.getInstance().getRestartLocation(restart.getRedirectId());
					activeChar.sendMessage("Banned race: " + restart.getBannedRace().name());
					activeChar.sendMessage("Redirect To: " + redirect.getName() + " (" + redirect.getLocName() + ")");
				}
				
				Location loc;
				loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.Castle);
				activeChar.sendMessage("TeleToLocation (Castle): x:" + loc.getX() + " y:" + loc.getY() + " z:"
						+ loc.getZ());
				
				loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.ClanHall);
				activeChar.sendMessage("TeleToLocation (ClanHall): x:" + loc.getX() + " y:" + loc.getY() + " z:"
						+ loc.getZ());
				
				loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.SiegeFlag);
				activeChar.sendMessage("TeleToLocation (SiegeFlag): x:" + loc.getX() + " y:" + loc.getY() + " z:"
						+ loc.getZ());
				
				loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.Town);
				activeChar.sendMessage("TeleToLocation (Town): x:" + loc.getX() + " y:" + loc.getY() + " z:"
						+ loc.getZ());
				
				String nearestTown = TownManager.getInstance().getClosestTownName(activeChar);
				Announcements.getInstance().announceToAll(
						activeChar.getName() + " has tried spawn-announce near " + nearestTown + "!");
			}
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
