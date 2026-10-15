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

import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.zone.L2Zone;

public class AdminZone implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_zone_check", "admin_zone_reload" };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#useAdminCommand(java.lang.String, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		
		if (actualCommand.equalsIgnoreCase("admin_zone_check"))
		{
			int zones = 0;
			for (L2Zone zone : activeChar.getWorldRegion().getZones())
			{
				if (zone.isInsideZone(activeChar.getX(), activeChar.getY()))
				{
					zones++;
					activeChar.sendMessage("Zone (XY" + (zone.isInsideZone(activeChar) ? ("Z) ") : (") ")) + "Type: "
							+ zone.getClassName() + ", " + "ID: " + zone.getId() + ", " + "Name: " + zone.getName()
							+ ", " + "Z[" + zone.getMinZ(activeChar) + ":" + zone.getMaxZ(activeChar) + "]");
				}
			}
			if (zones == 0)
				activeChar.sendMessage("No zones");
			activeChar.sendMessage("Closest Castle: "
					+ CastleManager.getInstance().getClosestCastle(activeChar).getName());
			activeChar.sendMessage("Closest Town: " + TownManager.getInstance().getClosestTownName(activeChar));
			
			Location loc;
			
			loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.Castle);
			activeChar
					.sendMessage("TeleToLocation (Castle): x:" + loc.getX() + " y:" + loc.getY() + " z:" + loc.getZ());
			
			loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.ClanHall);
			activeChar.sendMessage("TeleToLocation (ClanHall): x:" + loc.getX() + " y:" + loc.getY() + " z:"
					+ loc.getZ());
			
			loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.SiegeFlag);
			activeChar.sendMessage("TeleToLocation (SiegeFlag): x:" + loc.getX() + " y:" + loc.getY() + " z:"
					+ loc.getZ());
			
			loc = MapRegionManager.getInstance().getTeleToLocation(activeChar, TeleportWhereType.Town);
			activeChar.sendMessage("TeleToLocation (Town): x:" + loc.getX() + " y:" + loc.getY() + " z:" + loc.getZ());
		}
		else if (actualCommand.equalsIgnoreCase("admin_zone_reload"))
		{
			ZoneManager.getInstance().reload();
			GmListTable.broadcastMessageToGMs("Zones reloaded.");
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
