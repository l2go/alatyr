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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.PetitionManager;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * This class handles commands for GMs to respond to petitions.
 * 
 * @author Tempy
 * 
 */
public class AdminPetition implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_view_petitions", "admin_view_petition",
			"admin_accept_petition", "admin_reject_petition", "admin_reset_petitions", "admin_force_peti",
			"admin_add_peti_chat" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		L2Object targetChar = activeChar.getTarget();
		
		int petitionId = -1;
		
		try
		{
			petitionId = Integer.parseInt(command.split(" ")[1]);
		}
		catch (Exception e)
		{
		}
		
		if (command.equals("admin_view_petitions"))
			PetitionManager.getInstance().sendPendingPetitionList(activeChar);
		else if (command.startsWith("admin_view_petition"))
			PetitionManager.getInstance().viewPetition(activeChar, petitionId);
		else if (command.startsWith("admin_accept_petition"))
		{
			if (PetitionManager.getInstance().isPlayerInConsultation(activeChar))
			{
				activeChar.sendPacket(SystemMessageId.ONLY_ONE_ACTIVE_PETITION_AT_TIME);
				return true;
			}
			
			if (PetitionManager.getInstance().isPetitionInProcess(petitionId))
			{
				activeChar.sendPacket(SystemMessageId.PETITION_UNDER_PROCESS);
				return true;
			}
			
			if (!PetitionManager.getInstance().acceptPetition(activeChar, petitionId))
				activeChar.sendPacket(SystemMessageId.NOT_UNDER_PETITION_CONSULTATION);
		}
		else if (command.startsWith("admin_reject_petition"))
		{
			if (!PetitionManager.getInstance().rejectPetition(activeChar, petitionId))
				activeChar.sendPacket(SystemMessageId.FAILED_CANCEL_PETITION_TRY_LATER);
		}
		else if (command.equals("admin_reset_petitions"))
		{
			if (PetitionManager.getInstance().isPetitionInProcess())
			{
				activeChar.sendPacket(SystemMessageId.PETITION_UNDER_PROCESS);
				return false;
			}
			PetitionManager.getInstance().clearPendingPetitions();
		}
		else if (command.startsWith("admin_force_peti"))
		{
			try
			{
				if (targetChar == null || !(targetChar instanceof L2Player))
				{
					activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT); // incorrect target!
					return false;
				}
				L2Player targetPlayer = (L2Player)targetChar;
				
				String val = command.substring(15);
				
				petitionId = PetitionManager.getInstance().submitPetition(targetPlayer, val, 9);
				PetitionManager.getInstance().acceptPetition(activeChar, petitionId);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				activeChar.sendMessage("Usage: //force_peti text");
				return false;
			}
		}
		else if (command.startsWith("admin_add_peti_chat"))
		{
			L2Player player = L2World.getInstance().getPlayer(command.substring(20));
			if (player == null)
			{
				activeChar.sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
				return false;
			}
			petitionId = PetitionManager.getInstance().submitPetition(player, "", 9);
			PetitionManager.getInstance().acceptPetition(activeChar, petitionId);
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
