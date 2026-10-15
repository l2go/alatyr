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
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SiegeAttackerList;
import com.l2jfree.gameserver.network.packets.server.SiegeDefenderList;

public class RequestJoinSiege extends L2ClientPacket
{
	private static final String _C__A4_RequestJoinSiege = "[C] a4 RequestJoinSiege";
	
	private int _siegeableID;
	private int _isAttacker;
	private int _isJoining;
	
	@Override
	protected void readImpl()
	{
		_siegeableID = readD();
		_isAttacker = readD();
		_isJoining = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Clan clan = activeChar.getClan();
		if (clan == null || !L2Clan.checkPrivileges(activeChar, L2Clan.CP_CS_MANAGE_SIEGE))
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		ClanHall hideout = null;
		Castle castle = CastleManager.getInstance().getCastleById(_siegeableID);
		if (castle == null)
			hideout = ClanHallManager.getInstance().getClanHallById(_siegeableID);
		
		if (_isJoining == 1 && System.currentTimeMillis() < clan.getDissolvingExpiryTime())
		{
			requestFailed(SystemMessageId.CANT_PARTICIPATE_IN_SIEGE_WHILE_DISSOLUTION_IN_PROGRESS);
			return;
		}
		
		if (castle != null)
		{
			if (castle.getSiege().getIsInProgress())
			{
				requestFailed(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
				return;
			}
			if (_isJoining == 1)
			{
				if (_isAttacker == 1)
					castle.getSiege().registerAttacker(activeChar);
				else
					castle.getSiege().registerDefender(activeChar);
			}
			else
				castle.getSiege().removeSiegeClan(activeChar);
			//castle.getSiege().listRegisterClan(activeChar);
			if (_isAttacker == 1)
				sendPacket(new SiegeAttackerList(castle));
			else
				sendPacket(new SiegeDefenderList(castle));
		}
		else if (hideout != null)
		{
			if (hideout.getSiege().getIsInProgress())
			{
				requestFailed(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
				return;
			}
			if (_isJoining == 1)
			{
				if (_isAttacker == 1)
					hideout.getSiege().registerAttacker(activeChar, false);
				else
					sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
			}
			else
				hideout.getSiege().removeSiegeClan(activeChar);
			//hideout.getSiege().listRegisterClan(activeChar);
			if (_isAttacker == 1)
				sendPacket(new SiegeAttackerList(hideout));
			else
				sendPacket(new SiegeDefenderList(hideout));
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__A4_RequestJoinSiege;
	}
}
