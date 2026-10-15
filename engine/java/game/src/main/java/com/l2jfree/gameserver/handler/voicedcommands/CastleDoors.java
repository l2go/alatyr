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
package com.l2jfree.gameserver.handler.voicedcommands;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.handler.IVoicedCommandHandler;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.network.SystemMessageId;

public class CastleDoors implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "open", "close" };
	
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (command.startsWith("open") && target.equals("doors") && (activeChar.isClanLeader()))
		{
			if (activeChar.getTarget() instanceof L2DoorInstance)
			{
				L2DoorInstance door = (L2DoorInstance)activeChar.getTarget();
				Castle castle = CastleManager.getInstance().getCastleById(activeChar.getClan().getHasCastle());
				
				if (door != null && castle != null && castle.checkIfInZone(door.getX(), door.getY(), door.getZ()))
				{
					if (Config.SIEGE_GATE_CONTROL || !castle.getSiege().getIsInProgress())
						door.openMe();
					else
						activeChar.sendPacket(SystemMessageId.GATES_NOT_OPENED_CLOSED_DURING_SIEGE);
				}
			}
			
			return true;
		}
		else if (command.startsWith("close") && target.equals("doors") && (activeChar.isClanLeader()))
		{
			if (activeChar.getTarget() instanceof L2DoorInstance)
			{
				L2DoorInstance door = (L2DoorInstance)activeChar.getTarget();
				Castle castle = CastleManager.getInstance().getCastleById(activeChar.getClan().getHasCastle());
				
				if (door != null && castle != null && castle.checkIfInZone(door.getX(), door.getY(), door.getZ()))
				{
					if (Config.SIEGE_GATE_CONTROL || !castle.getSiege().getIsInProgress())
						door.closeMe();
					else
						activeChar.sendPacket(SystemMessageId.GATES_NOT_OPENED_CLOSED_DURING_SIEGE);
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
