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
package com.l2jfree.gameserver.handler.usercommands;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IUserCommandHandler;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.entity.Town;
import com.l2jfree.gameserver.model.mapregion.L2MapRegion;
import com.l2jfree.gameserver.model.mapregion.L2MapRegionRestart;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class Loc implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS = { 0 };
	
	@Override
	public boolean useUserCommand(int id, L2Player activeChar)
	{
		L2MapRegionRestart restart = null;
		SystemMessageId msg = SystemMessageId.LOC_ADEN_S1_S2_S3;
		
		// Standard Town
		Town town = TownManager.getInstance().getTown(Config.ALT_DEFAULT_RESTARTTOWN);
		if (town != null && town.getMapRegion() != null)
		{
			restart = MapRegionManager.getInstance().getRestartLocation(town.getMapRegion().getRestartId());
			if (restart != null)
				msg = SystemMessageId.getSystemMessageId(restart.getLocName());
		}
		
		L2MapRegion region = MapRegionManager.getInstance().getRegion(activeChar);
		if (region != null)
		{
			int restartId = region.getRestartId();
			restart = MapRegionManager.getInstance().getRestartLocation(restartId);
			msg = SystemMessageId.getSystemMessageId(restart.getLocName());
		}
		
		SystemMessage sm = new SystemMessage(msg);
		sm.addNumber(activeChar.getX());
		sm.addNumber(activeChar.getY());
		sm.addNumber(activeChar.getZ());
		activeChar.sendPacket(sm);
		
		if (Config.ALT_SHOW_RESTART_TOWN && restart != null)
		{
			if (restart.getLocName() < 1222)
			{
				if (restart.getLocName() != 943)
				{
					activeChar.sendPacket(SystemMessageId.getSystemMessageId(msg.getId() + 31));
				}
				else
					// system message has a typo
					activeChar.sendMessage("Restart at the Town of Gludio.");
			}
			else
			{
				if (SystemMessageId.LOC_GM_CONSULATION_SERVICE_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the GM Consulation Service.");
				else if (SystemMessageId.LOC_RUNE_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at Rune Township.");
				else if (SystemMessageId.LOC_GODDARD_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the Town of Goddard.");
				else if (SystemMessageId.LOC_DIMENSIONAL_GAP_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the Dimensional Gap.");
				else if (SystemMessageId.LOC_CEMETARY_OF_THE_EMPIRE_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the Cemetary of the Empire.");
				else if (SystemMessageId.LOC_SCHUTTGART_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the Town of Schuttgart.");
				else if (SystemMessageId.LOC_PRIMEVAL_ISLE_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at the Primeval Isle.");
				else if (SystemMessageId.LOC_KAMAEL_VILLAGE_S1_S2_S3.getId() == restart.getLocName())
					activeChar.sendMessage("Restart at Kamael Village.");
			}
		}
		
		return true;
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
