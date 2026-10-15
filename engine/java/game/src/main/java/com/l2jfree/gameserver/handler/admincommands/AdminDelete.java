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

import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.GrandBossSpawnManager;
import com.l2jfree.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * This class handles following admin commands: - delete = deletes target
 * 
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2005/04/11 10:05:56 $
 */
public class AdminDelete implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_delete" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_delete"))
			handleDelete(activeChar);
		
		return true;
		
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void handleDelete(L2Player activeChar)
	{
		L2Object obj = activeChar.getTarget();
		if ((obj != null) && (obj instanceof L2Npc))
		{
			L2Npc target = (L2Npc)obj;
			target.deleteMe();
			
			L2Spawn spawn = target.getSpawn();
			if (spawn != null)
			{
				spawn.stopRespawn();
				
				if (RaidBossSpawnManager.getInstance().isDefined(spawn.getNpcId()))
					RaidBossSpawnManager.getInstance().deleteSpawn(spawn, true);
				else if (GrandBossSpawnManager.getInstance().isDefined(spawn.getNpcId()))
					GrandBossSpawnManager.getInstance().deleteSpawn(spawn, true);
				else
					SpawnTable.getInstance().deleteSpawn(spawn, true);
			}
			
			activeChar.sendMessage("Deleted " + target.getName() + " from " + target.getObjectId() + ".");
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
		}
	}
}
