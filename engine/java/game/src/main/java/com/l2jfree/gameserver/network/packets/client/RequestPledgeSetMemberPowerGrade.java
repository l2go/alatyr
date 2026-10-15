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
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Format: (ch) Sd
 * @author  -Wooden-
 * 
 */
public class RequestPledgeSetMemberPowerGrade extends L2ClientPacket
{
	private static final String _C__D0_1C_REQUESTPLEDGESETMEMBERPOWERGRADE =
			"[C] D0:1C RequestPledgeSetMemberPowerGrade";
	
	private int _pledgeRank;
	private String _member;
	
	@Override
	protected void readImpl()
	{
		_member = readS();
		_pledgeRank = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Clan clan = activeChar.getClan();
		L2ClanMember member = null;
		if (clan != null)
			member = clan.getClanMember(_member);
		
		if (clan == null || member == null)
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			return;
		}
		
		if (member.getSubPledgeType() == L2Clan.SUBUNIT_ACADEMY)
		{
			// also checked from client side
			//activeChar.sendMessage("You cannot change academy member grade");
			sendAF();
			return;
		}
		
		member.setPledgeRank(_pledgeRank);
		clan.broadcastClanStatus();
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_1C_REQUESTPLEDGESETMEMBERPOWERGRADE;
	}
}
