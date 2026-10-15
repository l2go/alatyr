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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.JoinPledge;
import com.l2jfree.gameserver.network.packets.server.PledgeShowInfoUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAdd;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListAll;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class represents a packet that is sent by the client when a player confirms/denies
 * clan invitation.
 *
 * @version $Revision: 1.4.2.1.2.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestAnswerJoinPledge extends L2ClientPacket
{
	private static final String _C__25_REQUESTANSWERJOINPLEDGE = "[C] 25 RequestAnswerJoinPledge";
	
	private int _answer;
	
	@Override
	protected void readImpl()
	{
		_answer = readD();
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
			sendAF();
			return;
		}
		
		if (_answer == 0)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_DID_NOT_RESPOND_TO_S1_CLAN_INVITATION);
			sm.addString(requestor.getName());
			sendPacket(sm);
			sm = new SystemMessage(SystemMessageId.S1_DID_NOT_RESPOND_TO_CLAN_INVITATION);
			sm.addString(activeChar.getName());
			requestor.sendPacket(sm);
			sm = null;
		}
		else
		{
			if (!(requestor.getRequest().getRequestPacket() instanceof RequestJoinPledge))
			{
				sendAF();
				return; // hax
			}
			
			RequestJoinPledge requestPacket = (RequestJoinPledge)requestor.getRequest().getRequestPacket();
			L2Clan clan = requestor.getClan();
			// we must double check this cause during response time conditions can be changed, i.e. another player could join clan
			if (clan.checkClanJoinCondition(requestor, activeChar, requestPacket.getSubPledgeType()))
			{
				sendPacket(new JoinPledge(requestor.getClanId()));
				
				activeChar.setSubPledgeType(requestPacket.getSubPledgeType());
				if (requestPacket.getSubPledgeType() == L2Clan.SUBUNIT_ACADEMY)
				{
					activeChar.setPledgeRank(9); // academy
					activeChar.setLvlJoinedAcademy(activeChar.getLevel());
				}
				else
					activeChar.setPledgeRank(5); // new member starts at 5, not confirmed
					
				clan.addClanMember(activeChar);
				activeChar.setClanPrivileges(activeChar.getClan().getRankPrivs(activeChar.getPledgeRank()));
				sendPacket(SystemMessageId.ENTERED_THE_CLAN);
				
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_JOINED_CLAN);
				sm.addString(activeChar.getName());
				clan.broadcastToOnlineMembers(sm);
				
				L2ServerPacket pledge = new PledgeShowMemberListAdd(activeChar);
				clan.broadcastToOtherOnlineMembers(pledge, activeChar);
				pledge = new PledgeShowInfoUpdate(clan);
				clan.broadcastToOnlineMembers(pledge);
				
				// this activates the clan tab on the new member
				sendPacket(new PledgeShowMemberListAll(clan));
				activeChar.setClanJoinExpiryTime(0);
				activeChar.broadcastUserInfo();
				
				activeChar.enableResidentialSkills(true);
			}
		}
		
		activeChar.getRequest().onRequestResponse();
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__25_REQUESTANSWERJOINPLEDGE;
	}
}
