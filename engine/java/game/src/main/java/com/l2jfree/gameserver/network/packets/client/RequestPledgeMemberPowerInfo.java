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

import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.PledgeReceivePowerInfo;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestPledgeMemberPowerInfo extends L2ClientPacket
{
	private static final String _C__24_REQUESTJOINPLEDGE = "[C] 24 RequestPledgeMemberPowerInfo";
	
	@SuppressWarnings("unused")
	private int _unk1;
	private String _target;
	
	@Override
	protected void readImpl()
	{
		_unk1 = readD();
		_target = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Clan clan = getClient().getActiveChar().getClan();
		if (clan != null)
		{
			L2ClanMember cm = clan.getClanMember(_target);
			if (cm != null && cm.isOnline())
			{
				getClient().getActiveChar().sendPacket(new PledgeReceivePowerInfo(cm));
				//_log.warn("Everything is Ok with this packet: "+_target);
			}
			//else
			//_log.warn("Wtf is worng with this packet");
		}
		//_log.warn("Wtf is worng with this packet, no clan?!?!?!?!?");
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.clientpackets.ClientBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__24_REQUESTJOINPLEDGE;
	}
}
