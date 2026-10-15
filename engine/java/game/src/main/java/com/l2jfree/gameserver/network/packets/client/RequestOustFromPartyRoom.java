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
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * Sent when a player selects a party room member and clicks the
 * "Kick" button.
 * format (ch) d
 * @author -Wooden- (format)
 * @author Myzreal (implementation)
 */
public class RequestOustFromPartyRoom extends L2ClientPacket
{
	private static final String _C__D0_09_REQUESTOUSTFROMPARTYROOM = "[C] D0:09 RequestOustFromPartyRoom";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		L2Player target = L2World.getInstance().findPlayer(_objectId);
		if (target == null || target == activeChar)
		{
			sendAF();
			return;
		}
		
		L2Party party = target.getParty();
		if (party != null && party.isInDimensionalRift()
				&& !party.getDimensionalRift().getRevivedAtWaitingRoom().contains(activeChar))
		{
			requestFailed(SystemMessageId.COULD_NOT_OUST_FROM_PARTY);
			return;
		}
		
		L2PartyRoom room = activeChar.getPartyRoom();
		if (room != null && room.getLeader() == activeChar)
		{
			if (party != null)
				party.removePartyMember(target, true);
			else
				room.removeMember(target, true);
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_09_REQUESTOUSTFROMPARTYROOM;
	}
}
