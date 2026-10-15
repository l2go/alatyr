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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2Clan.SubPledge;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class PledgeShowMemberListAll extends L2ServerPacket
{
	private static final String _S__68_PLEDGESHOWMEMBERLISTALL = "[S] 53 PledgeShowMemberListAll";
	
	private static final int _pledgeType = 0;
	
	private final L2Clan _clan;
	private final L2ClanMember[] _members;
	
	public PledgeShowMemberListAll(L2Clan clan)
	{
		_clan = clan;
		_members = _clan.getMembers();
	}
	
	@Override
	public void packetSent(L2Client client, L2Player activeChar)
	{
		for (SubPledge element : _clan.getAllSubPledges())
			activeChar.sendPacket(new PledgeReceiveSubPledgeCreated(element, _clan));
		
		for (L2ClanMember cm : _members)
			if (cm.getSubPledgeType() != _pledgeType)
				activeChar.sendPacket(new PledgeShowMemberListAdd(cm));
		
		// unless this is sent sometimes, the client doesn't recognise the player as the leader
		activeChar.sendPacket(new UserInfo(activeChar));
	}
	
	@Override
	protected void writeImpl()
	{
		int mainOrSubpledge = 0;
		
		writeC(0x5a);
		
		writeD(mainOrSubpledge);
		writeD(_clan.getClanId());
		writeD(_pledgeType);
		writeS(_clan.getName());
		writeS(_clan.getLeaderName());
		
		writeD(_clan.getCrestId()); // crest id .. is used again
		writeD(_clan.getLevel());
		writeD(_clan.getHasCastle());
		writeD(_clan.getHasHideout());
		writeD(_clan.getHasFort());
		writeD(_clan.getRank()); // not confirmed
		writeD(_clan.getReputationScore()); //was activechar lvl
		writeD(0); //0
		writeD(0); //0
		writeD(_clan.getAllyId());
		writeS(_clan.getAllyName());
		writeD(_clan.getAllyCrestId());
		writeD(_clan.isAtWar() ? 1 : 0);// new c3
		
		if (Config.PACKET_FINAL)
			writeD(0); // Territory castle ID
			
		writeD(_clan.getSubPledgeMembersCount(_pledgeType));
		
		for (L2ClanMember m : _members)
		{
			if (m.getSubPledgeType() != _pledgeType)
				continue;
			writeS(m.getName());
			writeD(m.getLevel());
			writeD(m.getClassId());
			writeD(m.getSex());
			writeD(m.getRace());
			writeD(m.isOnline() ? m.getObjectId() : 0); // objectId=online 0=offline
			writeD(m.getSponsor() != 0 ? 1 : 0);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__68_PLEDGESHOWMEMBERLISTALL;
	}
}
