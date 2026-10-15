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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class RequestWithdrawAlly extends L2ClientPacket
{
	private static final String _C__REQUESTWITHDRAWALLY = "[C] 8E RequestWithdrawAlly c";
	
	@Override
	protected void readImpl()
	{
		// trigger packet
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		else if (player.getClan() == null)
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			return;
		}
		else if (!player.isClanLeader())
		{
			requestFailed(SystemMessageId.ONLY_CLAN_LEADER_WITHDRAW_ALLY);
			return;
		}
		
		L2Clan clan = player.getClan();
		if (clan.getAllyId() == 0)
		{
			requestFailed(SystemMessageId.NO_CURRENT_ALLIANCES);
			return;
		}
		else if (clan.getClanId() == clan.getAllyId())
		{
			requestFailed(SystemMessageId.ALLIANCE_LEADER_CANT_WITHDRAW);
			return;
		}
		
		long currentTime = System.currentTimeMillis();
		clan.setAllyId(0);
		clan.setAllyName(null);
		clan.setAllyCrestId(0);
		clan.setAllyPenaltyExpiryTime(currentTime + Config.ALT_ALLY_JOIN_DAYS_WHEN_LEAVED * 86400000L,
				L2Clan.PENALTY_TYPE_CLAN_LEAVED);
		clan.updateClanInDB();
		sendPacket(SystemMessageId.YOU_HAVE_WITHDRAWN_FROM_ALLIANCE);
		
		sendAF();
		
		for (L2Player member : player.getClan().getOnlineMembers(0))
			member.broadcastUserInfo();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTWITHDRAWALLY;
	}
}
