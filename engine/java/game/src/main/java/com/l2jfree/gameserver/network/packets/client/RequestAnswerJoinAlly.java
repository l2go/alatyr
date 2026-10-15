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
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 *  sample
 *  5F
 *  01 00 00 00
 *
 *  format  cdd
 *
 *
 * @version $Revision: 1.7.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestAnswerJoinAlly extends L2ClientPacket
{
	private static final String _C__83_REQUESTANSWERJOINALLY = "[C] 83 RequestAnswerJoinAlly";
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		L2Player requestor = activeChar.getRequest().getPartner();
		if (requestor == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (_response == 0)
		{
			sendPacket(SystemMessageId.YOU_DID_NOT_RESPOND_TO_ALLY_INVITATION);
			requestor.sendPacket(SystemMessageId.NO_RESPONSE_TO_ALLY_INVITATION);
		}
		else
		{
			if (!(requestor.getRequest().getRequestPacket() instanceof RequestJoinAlly))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return; // hax
			}
			
			L2Clan clan = requestor.getClan();
			// we must double check this cause of hack
			if (L2Clan.checkAllyJoinCondition(requestor, activeChar))
			{
				requestor.sendPacket(SystemMessageId.YOU_INVITED_FOR_ALLIANCE);
				sendPacket(SystemMessageId.YOU_ACCEPTED_ALLIANCE);
				
				activeChar.getClan().setAllyId(clan.getAllyId());
				activeChar.getClan().setAllyName(clan.getAllyName());
				activeChar.getClan().setAllyPenaltyExpiryTime(0, 0);
				activeChar.getClan().setAllyCrestId(clan.getAllyCrestId());
				activeChar.getClan().updateClanInDB();
				
				// Added to set the Alliance Crest when a clan joins an ally.
				activeChar.getClan().setAllyCrestId(requestor.getClan().getAllyCrestId());
				for (L2Player member : activeChar.getClan().getOnlineMembers(0))
					member.broadcastUserInfo();
			}
		}
		
		activeChar.getRequest().onRequestResponse();
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__83_REQUESTANSWERJOINALLY;
	}
}
