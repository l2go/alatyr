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
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class RequestStartPledgeWar extends L2ClientPacket
{
	private static final String _C__REQUESTSTARTPLEDGEWAR = "[C] 03 RequestStartPledgewar c[s]";
	
	private String _pledgeName;
	
	@Override
	protected void readImpl()
	{
		_pledgeName = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		L2Clan clan = player.getClan();
		if (!L2Clan.checkPrivileges(player, L2Clan.CP_CL_PLEDGE_WAR))
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		else if (clan.getLevel() < 3 || clan.getMembersCount() < Config.ALT_CLAN_MEMBERS_FOR_WAR)
		{
			requestFailed(SystemMessageId.CLAN_WAR_DECLARED_IF_CLAN_LVL3_OR_15_MEMBER);
			return;
		}
		
		L2Clan warClan = ClanTable.getInstance().getClanByName(_pledgeName);
		if (warClan == null)
		{
			requestFailed(SystemMessageId.CLAN_WAR_CANNOT_DECLARED_CLAN_NOT_EXIST);
			return;
		}
		else if (clan.getAllyId() != 0 && clan.getAllyId() == warClan.getAllyId())
		{
			requestFailed(SystemMessageId.CLAN_WAR_AGAINST_A_ALLIED_CLAN_NOT_WORK);
			return;
		}
		else if (warClan.getLevel() < 3 || warClan.getMembersCount() < Config.ALT_CLAN_MEMBERS_FOR_WAR)
		{
			SystemMessage sm =
					new SystemMessage(SystemMessageId.S1_CANNOT_DECLARE_WAR_TOO_LOW_LEVEL_OR_NOT_ENOUGH_MEMBERS);
			sm.addString(warClan.getName());
			requestFailed(sm);
			return;
		}
		else if (warClan.getDissolvingExpiryTime() > 0)
		{
			requestFailed(SystemMessageId.NO_CLAN_WAR_AGAINST_DISSOLVING_CLAN);
			return;
		}
		else if (clan.isAtWarWith(warClan.getClanId()))
		{
			sendAF();
			return;
		}
		
		//_log.warn("RequestStartPledgeWar, leader: " + clan.getLeaderName() + " clan: " + _clan.getName());
		
		//        L2Player leader = L2World.getInstance().getPlayer(clan.getLeaderName());
		
		//        if(leader == null)
		//            return;
		
		//        if(leader != null && leader.isOnline() == 0)
		//        {
		//            player.sendMessage("Clan leader isn't online.");
		//            player.sendPacket(new ActionFailed());
		//            return;
		//        }
		
		//        if (leader.isProcessingRequest())
		//        {
		//            SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER);
		//            sm.addString(leader.getName());
		//            player.sendPacket(sm);
		//            return;
		//        }
		
		//        if (leader.isTransactionInProgress())
		//        {
		//            SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER);
		//            sm.addString(leader.getName());
		//            player.sendPacket(sm);
		//            return;
		//        }
		
		//        leader.setTransactionRequester(player);
		//        player.setTransactionRequester(leader);
		//        leader.sendPacket(new StartPledgeWar(_clan.getName(),player.getName()));
		ClanTable.getInstance().storeclanswars(clan.getClanId(), warClan.getClanId());
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTSTARTPLEDGEWAR;
	}
}
