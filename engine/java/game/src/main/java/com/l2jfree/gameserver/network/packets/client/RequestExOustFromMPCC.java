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
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author -Wooden-
 */
public class RequestExOustFromMPCC extends L2ClientPacket
{
	private static final String _C__D0_0F_REQUESTEXOUSTFROMMPCC = "[C] D0:0F RequestExOustFromMPCC";
	
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (!activeChar.isInParty() || !activeChar.getParty().isInCommandChannel())
		{
			requestFailed(SystemMessageId.NO_USER_INVITED_TO_COMMAND_CHANNEL);
			return;
		}
		else if (!activeChar.getParty().getCommandChannel().getChannelLeader().equals(activeChar))
		{
			requestFailed(SystemMessageId.CANT_USE_COMMAND_CHANNEL);
			return;
		}
		
		L2Player target = L2World.getInstance().getPlayer(_name);
		if (target == null || !target.isInParty() || !target.getParty().isInCommandChannel())
		{
			requestFailed(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		
		target.getParty().getCommandChannel().removeParty(target.getParty());
		
		SystemMessage sm = SystemMessageId.DISMISSED_FROM_COMMAND_CHANNEL.getSystemMessage();
		target.getParty().broadcastToPartyMembers(sm);
		
		sm = new SystemMessage(SystemMessageId.C1_PARTY_DISMISSED_FROM_COMMAND_CHANNEL);
		sm.addString(target.getParty().getPartyMembers().get(0).getName());
		sendPacket(sm);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_0F_REQUESTEXOUSTFROMMPCC;
	}
}
