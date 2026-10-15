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
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExMPCCShowPartyMemberInfo;

/**
 * Format:(ch) d
 * @author  Crion/kombat
 */
public final class RequestExMPCCShowPartyMembersInfo extends L2ClientPacket
{
	private static final String _C__D0_26_REQUESTMPCCSHOWPARTYMEMBERINFO =
			"[C] D0:26 RequestExMPCCShowPartyMembersInfo";
	
	private int _leaderId;
	
	@Override
	protected void readImpl()
	{
		_leaderId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		if (player.getParty() == null || player.getParty().getCommandChannel() == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		for (L2Party party : player.getParty().getCommandChannel().getPartys())
		{
			if (party.getLeader().getObjectId() == _leaderId)
			{
				sendPacket(new ExMPCCShowPartyMemberInfo(party));
				break;
			}
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__D0_26_REQUESTMPCCSHOWPARTYMEMBERINFO;
	}
}
