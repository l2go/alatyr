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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.JoinParty;

public class RequestAnswerJoinParty extends L2ClientPacket
{
	private static final String _C__REQUESTANSWERPARTY = "[C] 43 RequestAnswerJoinParty c[d]";
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		L2Player requestor = player.getActiveRequester();
		if (requestor == null)
		{
			sendAF();
			return;
		}
		
		if (_response == 1)
		{
			requestor.sendPacket(JoinParty.ACCEPTED);
			if (requestor.getParty() != null)
			{
				if (requestor.getParty().getMemberCount() >= 9)
				{
					requestor.sendPacket(SystemMessageId.PARTY_FULL);
					requestFailed(SystemMessageId.PARTY_FULL);
					return;
				}
			}
			player.joinParty(requestor.getParty());
		}
		else
		{
			requestor.sendPacket(JoinParty.DECLINED);
			requestor.sendPacket(SystemMessageId.PLAYER_DECLINED_PARTY);
			
			L2Party party = requestor.getParty();
			//activate garbage collection if there are no other members in party (happens when we were creating a new one)
			if (party != null && party.getMemberCount() == 1)
			{
				L2PartyRoom room = party.getPartyRoom();
				if (room != null)
					room.setParty(null);
				party.setPartyRoom(null);
				requestor.setParty(null);
			}
		}
		
		sendAF();
		
		if (requestor.getParty() != null)
			requestor.getParty().setPendingInvitation(false); // if party is null, there is no need of decreasing
		player.setActiveRequester(null);
		requestor.onTransactionResponse();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTANSWERPARTY;
	}
}
