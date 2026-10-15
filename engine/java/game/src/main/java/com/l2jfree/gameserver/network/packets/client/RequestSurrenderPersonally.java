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

import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class RequestSurrenderPersonally extends L2ClientPacket
{
	private static final String _C__69_REQUESTSURRENDERPERSONALLY = "[C] 69 RequestSurrenderPersonally";
	
	private String _pledgeName;
	
	@Override
	protected void readImpl()
	{
		_pledgeName = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		_log.info("RequestSurrenderPersonally by " + activeChar.getName() + " with " + _pledgeName);
		
		L2Clan clan = activeChar.getClan();
		if (clan == null)
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			return;
		}
		
		L2Clan warClan = ClanTable.getInstance().getClanByName(_pledgeName);
		if (warClan == null)
		{
			requestFailed(SystemMessageId.CLAN_DOESNT_EXISTS);
			return;
		}
		
		if (!clan.isAtWarWith(warClan.getClanId()))
		{
			requestFailed(SystemMessageId.NOT_INVOLVED_IN_WAR);
			return;
		}
		else if (activeChar.getWantsPeace() == 1)
		{
			requestFailed(SystemMessageId.YOU_ARE_WORKING_WITH_ANOTHER_CLAN);
			return;
		}
		
		activeChar.setWantsPeace(1);
		activeChar.deathPenalty(false, false, false);
		SystemMessage msg = new SystemMessage(SystemMessageId.YOU_HAVE_PERSONALLY_SURRENDERED_TO_THE_S1_CLAN);
		msg.addString(_pledgeName);
		sendPacket(msg);
		ClanTable.getInstance().checkSurrender(clan, warClan);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__69_REQUESTSURRENDERPERSONALLY;
	}
}
