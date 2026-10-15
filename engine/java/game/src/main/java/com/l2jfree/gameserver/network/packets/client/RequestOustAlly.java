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
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class RequestOustAlly extends L2ClientPacket
{
	private static final String _C__REQUESTOUSTALLY = "[C] 8F RequestOustAlly c[s]";
	
	private String _clanName;
	
	@Override
	protected void readImpl()
	{
		_clanName = readS();
	}
	
	@Override
	protected void runImpl()
	{
		if (_clanName == null)
			return;
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		L2Clan leaderClan = player.getClan();
		if (leaderClan == null)
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			return;
		}
		else if (leaderClan.getAllyId() == 0)
		{
			requestFailed(SystemMessageId.NO_CURRENT_ALLIANCES);
			return;
		}
		else if (!player.isClanLeader() || leaderClan.getClanId() != leaderClan.getAllyId())
		{
			requestFailed(SystemMessageId.FEATURE_ONLY_FOR_ALLIANCE_LEADER);
			return;
		}
		
		L2Clan clan = ClanTable.getInstance().getClanByName(_clanName);
		if (clan == null)
		{
			requestFailed(SystemMessageId.CLAN_DOESNT_EXISTS);
			return;
		}
		else if (clan.getClanId() == leaderClan.getClanId())
		{
			requestFailed(SystemMessageId.ALLIANCE_LEADER_CANT_WITHDRAW);
			return;
		}
		else if (clan.getAllyId() != leaderClan.getAllyId())
		{
			requestFailed(SystemMessageId.DIFFERENT_ALLIANCE);
			return;
		}
		
		long currentTime = System.currentTimeMillis();
		leaderClan.setAllyPenaltyExpiryTime(currentTime + Config.ALT_ACCEPT_CLAN_DAYS_WHEN_DISMISSED * 86400000L,
				L2Clan.PENALTY_TYPE_DISMISS_CLAN);
		leaderClan.updateClanInDB();
		
		clan.setAllyId(0);
		clan.setAllyName(null);
		clan.setAllyCrestId(0);
		clan.setAllyPenaltyExpiryTime(currentTime + Config.ALT_ALLY_JOIN_DAYS_WHEN_DISMISSED * 86400000L,
				L2Clan.PENALTY_TYPE_CLAN_DISMISSED);
		clan.updateClanInDB();
		sendPacket(SystemMessageId.YOU_HAVE_EXPELED_A_CLAN);
		
		sendAF();
		
		for (L2Player member : player.getClan().getOnlineMembers(0))
			member.broadcastUserInfo();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTOUSTALLY;
	}
}
