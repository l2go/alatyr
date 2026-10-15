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
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.persistence.clan.ClanRepository;

/**
 * This class ...
 * 
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestSetAllyCrest extends L2ClientPacket
{
	private static final String _C__87_REQUESTSETALLYCREST = "[C] 87 RequestSetAllyCrest";
	
	private int _length;
	
	private byte[] _data;
	
	@Override
	protected void readImpl()
	{
		_length = readD();
		if (_length < 0 || _length > 192)
			return;
		
		_data = new byte[_length];
		readB(_data);
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Clan clan = ClanTable.getInstance().getClan(activeChar.getAllyId());
		
		if (clan == null || !activeChar.isClanLeader() || activeChar.getClanId() != clan.getClanId())
		{
			requestFailed(SystemMessageId.FEATURE_ONLY_FOR_ALLIANCE_LEADER);
			return;
		}
		
		if (_length < 0 || _length > 192)
		{
			requestFailed(SystemMessageId.INVALID_INSIGNIA_FORMAT);
			return;
		}
		
		CrestCache crestCache = CrestCache.getInstance();
		
		int newId = IdFactory.getInstance().getNextId();
		
		if (!crestCache.saveAllyCrest(newId, _data))
		{
			//all lies, the problem is server-side :D
			requestFailed(SystemMessageId.INVALID_INSIGNIA_COLOR);
			_log.info("Error saving alliance crest: " + clan.getAllyName());
			return;
		}
		
		try
		{
			// Every clan of the alliance shows the new crest
			ClanRepository.getInstance().updateAllianceCrest(clan.getAllyId(), newId);
		}
		catch (Exception e)
		{
			_log.warn("could not update the ally crest id:", e);
		}
		
		// The clans point at the new crest now, so the old image can go
		if (clan.getAllyCrestId() != 0)
			crestCache.removeAllyCrest(clan.getAllyCrestId());
		
		for (L2Clan c : ClanTable.getInstance().getClans())
		{
			if (c.getAllyId() == activeChar.getAllyId())
			{
				c.setAllyCrestId(newId);
				for (L2Player member : c.getOnlineMembers(0))
					member.broadcastUserInfo();
			}
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__87_REQUESTSETALLYCREST;
	}
}
