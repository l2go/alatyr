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
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Player.TeleportMode;
import com.l2jfree.gameserver.handler.IUserCommandHandler;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

public class Escape implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS = { 52 };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IUserCommandHandler#useUserCommand(int, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useUserCommand(int id, L2Player activeChar)
	{
		if (!activeChar.canTeleport(TeleportMode.UNSTUCK))
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		int unstuckTimer = (activeChar.getAccessLevel() >= Config.GM_ESCAPE ? 1000 : Config.UNSTUCK_INTERVAL * 1000);
		
		L2Skill GM_escape = SkillTable.getInstance().getInfo(2100, 1); // 1 second escape
		L2Skill escape = SkillTable.getInstance().getInfo(2099, 1); // 5 minutes escape
		if (activeChar.getAccessLevel() >= Config.GM_ESCAPE)
		{
			if (GM_escape != null)
			{
				activeChar.sendMessage("You use Escape: 1 second.");
				activeChar.useMagic(GM_escape, false, false);
				return true;
			}
		}
		else if (Config.UNSTUCK_INTERVAL == 300 && escape != null)
		{
			activeChar.useMagic(escape, false, false);
			return true;
		}
		else
		{
			if (Config.UNSTUCK_INTERVAL > 100)
			{
				activeChar.sendMessage("You use Escape: " + unstuckTimer / 60000 + " minutes.");
			}
			else
				activeChar.sendMessage("You use Escape: " + unstuckTimer / 1000 + " seconds.");
		}
		
		// Continue execution later
		activeChar.setTeleportSkillCast(new EscapeFinalizer(activeChar), unstuckTimer);
		
		return true;
	}
	
	static class EscapeFinalizer implements Runnable
	{
		private final L2Player _activeChar;
		
		EscapeFinalizer(L2Player activeChar)
		{
			_activeChar = activeChar;
		}
		
		@Override
		public void run()
		{
			_activeChar.setIsIn7sDungeon(false);
			_activeChar.setInstanceId(0);
			_activeChar.teleToLocation(TeleportWhereType.Town);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IUserCommandHandler#getUserCommandList()
	 */
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
