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

import com.l2jfree.gameserver.cache.CrestCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.persistence.clan.ClanRepository;

/**
 * Format : chdb
 * c (id) 0xD0
 * h (subid) 0x11
 * d data size
 * b raw data (picture i think ;) )
 * @author -Wooden-
 *
 */
public class RequestExSetPledgeCrestLarge extends L2ClientPacket
{
	private static final String _C__D0_11_REQUESTEXSETPLEDGECRESTLARGE = "[C] D0:11 RequestExSetPledgeCrestLarge";
	private static final int MAX_INSIGNIA_BYTESIZE = 2176;
	
	private int _size;
	private byte[] _data;
	
	@Override
	protected void readImpl()
	{
		_size = readD();
		if (_size > 0 && _size < MAX_INSIGNIA_BYTESIZE) // client CAN send a RequestExSetPledgeCrestLarge with the size set to 0 then format is just chd
		{
			_data = new byte[_size];
			readB(_data);
		}
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		SystemMessageId fail = null;
		L2Clan clan = activeChar.getClan();
		if (!L2Clan.checkPrivileges(activeChar, L2Clan.CP_CL_REGISTER_CREST))
			fail = SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT;
		else if (clan.getLevel() < 3)
			fail = SystemMessageId.CLAN_LVL_3_NEEDED_TO_SET_CREST;
		else if (clan.getDissolvingExpiryTime() > 0)
			fail = SystemMessageId.CANNOT_SET_CREST_WHILE_DISSOLUTION_IN_PROGRESS;
		else if (_size > MAX_INSIGNIA_BYTESIZE)
			fail = SystemMessageId.INVALID_INSIGNIA_FORMAT;
		
		if (fail != null)
		{
			requestFailed(fail);
			return;
		}
		
		CrestCache cc = CrestCache.getInstance();
		
		if (_data == null)
		{
			if (!cc.removePledgeCrestLarge(clan.getCrestLargeId()))
			{
				_log.warn("Error deleting large crest of clan:" + clan.getName());
				requestFailed(SystemMessageId.FILE_NOT_FOUND);
				return;
			}
			
			try
			{
				ClanRepository.getInstance().updateClanLargeCrest(clan.getClanId(), 0);
			}
			catch (Exception e)
			{
				_log.warn("could not clear the large crest id:", e);
			}
			clan.setCrestLargeId(0);
			clan.setHasCrestLarge(false);
			sendPacket(SystemMessageId.CLAN_CREST_HAS_BEEN_DELETED);
			for (L2Player member : clan.getOnlineMembers(0))
				member.broadcastUserInfo();
		}
		else if (clan.getHasCastle() > 0 || clan.getHasHideout() > 0)
		{
			int newId = IdFactory.getInstance().getNextId();
			if (!cc.savePledgeCrestLarge(newId, _data))
			{
				//all lies, the problem is server-side :D
				requestFailed(SystemMessageId.INVALID_INSIGNIA_COLOR);
				return;
			}
			
			try
			{
				ClanRepository.getInstance().updateClanLargeCrest(clan.getClanId(), newId);
			}
			catch (Exception e)
			{
				_log.warn("could not update the large crest id:", e);
			}
			
			// The clan points at the new crest now, so the old image can go
			if (clan.hasCrestLarge() && !cc.removePledgeCrestLarge(clan.getCrestLargeId()))
				_log.warn("Error deleting the old large crest of clan:" + clan.getName());
			
			clan.setCrestLargeId(newId);
			clan.setHasCrestLarge(true);
			
			sendPacket(SystemMessageId.CLAN_EMBLEM_WAS_SUCCESSFULLY_REGISTERED);
			
			for (L2Player member : clan.getOnlineMembers(0))
				member.broadcastUserInfo();
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__D0_11_REQUESTEXSETPLEDGECRESTLARGE;
	}
}
