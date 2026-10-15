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
import com.l2jfree.gameserver.model.L2CommandChannel;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 *
 * @author Chris
 */
public class ChannelLeave implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS = { 96 };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IUserCommandHandler#useUserCommand(int, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useUserCommand(int id, L2Player activeChar)
	{
		if (id != COMMAND_IDS[0])
			return false;
		
		if (activeChar.isInParty())
		{
			if (activeChar.getParty().isLeader(activeChar) && activeChar.getParty().isInCommandChannel())
			{
				L2Party party = activeChar.getParty();
				L2CommandChannel channel = party.getCommandChannel();
				channel.removeParty(party);
				
				SystemMessage sm = SystemMessageId.LEFT_COMMAND_CHANNEL.getSystemMessage();
				party.broadcastToPartyMembers(sm);
				sm = new SystemMessage(SystemMessageId.C1_PARTY_LEFT_COMMAND_CHANNEL);
				sm.addString(activeChar.getName());
				channel.broadcastToChannelMembers(sm);
				return true;
			}
			
			activeChar.sendPacket(SystemMessageId.ONLY_PARTY_LEADER_CAN_LEAVE_CHANNEL);
		}
		
		return false;
		
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
