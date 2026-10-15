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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.PetitionManager;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * <p>Format: (c) d
 * <ul>
 * <li>d: Unknown</li>
 * </ul></p>
 * 
 * @author -Wooden-, TempyIncursion
 */
public class RequestPetitionCancel extends L2ClientPacket
{
	private static final String _C__80_REQUEST_PETITIONCANCEL = "[C] 80 RequestPetitionCancel";
	
	//private int _unknown;
	
	@Override
	protected void readImpl()
	{
		//_unknown = readD(); This is pretty much a trigger packet.
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (PetitionManager.getInstance().isPlayerInConsultation(activeChar))
		{
			if (activeChar.isGM())
				PetitionManager.getInstance().endActivePetition(activeChar);
			else
				activeChar.sendPacket(SystemMessageId.PETITION_UNDER_PROCESS);
		}
		else
		{
			if (PetitionManager.getInstance().isPlayerPetitionPending(activeChar))
			{
				if (PetitionManager.getInstance().cancelActivePetition(activeChar))
				{
					int numRemaining =
							Config.MAX_PETITIONS_PER_PLAYER
									- PetitionManager.getInstance().getPlayerTotalPetitionCount(activeChar);
					
					SystemMessage sm = new SystemMessage(SystemMessageId.PETITION_CANCELED_SUBMIT_S1_MORE_TODAY);
					sm.addString(String.valueOf(numRemaining));
					activeChar.sendPacket(sm);
					
					// Notify all GMs that the player's pending petition has been cancelled.
					String msgContent = activeChar.getName() + " has canceled a pending petition.";
					GmListTable.broadcastToGMs(new CreatureSay(activeChar.getObjectId(), SystemChatChannelId.Chat_Hero,
							"Petition System", msgContent));
				}
				else
				{
					activeChar.sendPacket(SystemMessageId.FAILED_CANCEL_PETITION_TRY_LATER);
				}
			}
			else
			{
				activeChar.sendPacket(SystemMessageId.PETITION_NOT_SUBMITTED);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__80_REQUEST_PETITIONCANCEL;
	}
}
