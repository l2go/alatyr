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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IVoicedCommandHandler;
import com.l2jfree.gameserver.instancemanager.hellbound.HellboundManager;

/**
 * @author Psycho(killer1888) / L2jFree
 */
public class Hellbound implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "trust" };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#useVoicedCommand(String, com.l2jfree.gameserver.model.L2Player), String)
	 */
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (command.startsWith("trust"))
		{
			int points = HellboundManager.getInstance().getTrustPoints();
			int level = HellboundManager.getInstance().getCurrentLevel();
			
			activeChar.sendMessage("Hellbound current trust points: " + points);
			activeChar.sendMessage("Hellbound current level: " + level);
			return true;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#getVoicedCommandList()
	 */
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
