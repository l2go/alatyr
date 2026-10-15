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
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.PledgeInfo;

/**
 * This class ...
 * 
 * @version $Revision: 1.5.4.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestPledgeInfo extends L2ClientPacket
{
	private static final String _C__66_REQUESTPLEDGEINFO = "[C] 66 RequestPledgeInfo";
	
	private int _clanId;
	
	/**
	 * packet type id 0x66
	 * format:		cd
	 * @param rawPacket
	 */
	@Override
	protected void readImpl()
	{
		_clanId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		if (_log.isDebugEnabled())
			_log.debug("infos for clan " + _clanId + " requested");
		
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Clan clan = ClanTable.getInstance().getClan(_clanId);
		if (clan == null)
		{
			_log.warn("Clan data for clanId " + _clanId + " is missing for player " + activeChar.getName());
			return; // we have no clan data ?!? should not happen
		}
		
		activeChar.sendPacket(new PledgeInfo(clan));
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.clientpackets.ClientBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__66_REQUESTPLEDGEINFO;
	}
}
