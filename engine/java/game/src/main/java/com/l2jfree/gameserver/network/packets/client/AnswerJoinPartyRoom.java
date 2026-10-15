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

import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class AnswerJoinPartyRoom extends L2ClientPacket
{
	private static final String _C__ANSWERJOINPARTYROOM = "[C] D0:30 AnswerJoinPartyRoom ch[d]";
	
	private boolean _accepted;
	
	@Override
	protected void readImpl()
	{
		_accepted = (readD() == 1);
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.PC_ITERACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		L2Player requester = activeChar.getActiveRequester();
		if (requester == null)
		{
			sendAF();
			return;
		}
		
		if (_accepted) // takes care of everything
			L2PartyRoom.tryJoin(activeChar, requester.getPartyRoom(), true);
		else
			sendPacket(SystemMessageId.PARTY_MATCHING_REQUEST_NO_RESPONSE);
		
		// Clears requesting status
		activeChar.setActiveRequester(null);
		requester.onTransactionResponse();
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__ANSWERJOINPARTYROOM;
	}
}
