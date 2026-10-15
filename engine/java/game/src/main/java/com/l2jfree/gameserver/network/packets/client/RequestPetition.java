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
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class RequestPetition extends L2ClientPacket
{
	private static final String _C__REQUESTPETITION = "[C] 89 RequestPetition c[sd]";
	
	private String _content;
	private int _on;
	
	@Override
	protected void readImpl()
	{
		_content = readS();
		_on = readD()/* != 0*/;
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		if (!GmListTable.isAnyGmOnline(false))
		{
			sendPacket(SystemMessageId.NO_GM_PROVIDING_SERVICE_NOW);
			return;
		}
		
		if (!PetitionManager.getInstance().isPetitioningAllowed())
		{
			sendPacket(SystemMessageId.GAME_CLIENT_UNABLE_TO_CONNECT_TO_PETITION_SERVER);
			return;
		}
		
		if (PetitionManager.getInstance().isPlayerPetitionPending(activeChar))
		{
			sendPacket(SystemMessageId.ONLY_ONE_ACTIVE_PETITION_AT_TIME);
			return;
		}
		
		if (PetitionManager.getInstance().getPendingPetitionCount() == Config.MAX_PETITIONS_PENDING)
		{
			sendPacket(SystemMessageId.PETITION_UNAVAILABLE);
			return;
		}
		
		int totalPetitions = PetitionManager.getInstance().getPlayerTotalPetitionCount(activeChar) + 1;
		if (totalPetitions > Config.MAX_PETITIONS_PER_PLAYER)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.WE_HAVE_RECEIVED_S1_PETITIONS_TODAY);
			sm.addNumber(totalPetitions);
			sendPacket(sm);
			return;
		}
		
		if (_content.length() > 255)
		{
			sendPacket(SystemMessageId.PETITION_MAX_CHARS_255);
			return;
		}
		
		int petitionId = PetitionManager.getInstance().submitPetition(activeChar, _content, _on);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.PETITION_ACCEPTED_RECENT_NO_S1);
		sm.addNumber(petitionId);
		sendPacket(sm);
		
		sm = new SystemMessage(SystemMessageId.SUBMITTED_YOU_S1_TH_PETITION_S2_LEFT);
		sm.addNumber(totalPetitions);
		sm.addNumber(Config.MAX_PETITIONS_PER_PLAYER - totalPetitions);
		sendPacket(sm);
		
		sm = new SystemMessage(SystemMessageId.S1_PETITION_ON_WAITING_LIST);
		sm.addNumber(PetitionManager.getInstance().getPendingPetitionCount());
		sendPacket(sm);
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTPETITION;
	}
}
