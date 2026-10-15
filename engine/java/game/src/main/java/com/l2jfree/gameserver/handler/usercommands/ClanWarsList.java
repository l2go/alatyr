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
package com.l2jfree.gameserver.handler.usercommands;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IUserCommandHandler;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.clan.ClanRepository;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.WarList;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.WarOpponentRecord;

/**
 * Support for /ClanWarsList command
 * @author Tempy - 28 Jul 05
 */
public class ClanWarsList implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS = { 88, 89, 90 };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IUserCommandHandler#useUserCommand(int, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useUserCommand(int id, L2Player activeChar)
	{
		if (id != COMMAND_IDS[0] && id != COMMAND_IDS[1] && id != COMMAND_IDS[2])
			return false;
		
		L2Clan clan = activeChar.getClan();
		if (clan == null)
		{
			activeChar.sendPacket(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			return false;
		}
		
		try
		{
			WarList list;
			if (id == 88)
			{
				// Attack list
				activeChar.sendPacket(SystemMessageId.CLANS_YOU_DECLARED_WAR_ON);
				list = WarList.DECLARED;
			}
			else if (id == 89)
			{
				// Under attack list
				activeChar.sendPacket(SystemMessageId.CLANS_THAT_HAVE_DECLARED_WAR_ON_YOU);
				list = WarList.RECEIVED;
			}
			else
			// id = 90
			{
				// War list
				activeChar.sendPacket(SystemMessageId.WAR_LIST);
				list = WarList.MUTUAL;
			}
			for (WarOpponentRecord opponent : ClanRepository.getInstance().loadWarOpponents(clan.getClanId(), list))
			{
				SystemMessage sm = null;
				String clanName = opponent.clanName();
				if (opponent.allianceId() > 0)
				{
					//target with ally
					sm = new SystemMessage(SystemMessageId.S1_S2_ALLIANCE);
					sm.addString(clanName);
					sm.addString(opponent.allianceName());
				}
				else
				{
					//target without ally
					sm = new SystemMessage(SystemMessageId.S1_NO_ALLI_EXISTS);
					sm.addString(clanName);
				}
				activeChar.sendPacket(sm);
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IUserCommandHandler#getUserCommandList()
	 */
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
