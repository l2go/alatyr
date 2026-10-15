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
import com.l2jfree.gameserver.model.L2CommandChannel;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author -Wooden-
 */
public class RequestExAcceptJoinMPCC extends L2ClientPacket
{
	private static final String _C__D0_0E_REQUESTEXASKJOINMPCC = "[C] D0:0E RequestExAcceptJoinMPCC";
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		L2Player requestor = player.getActiveRequester();
		if (requestor == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		SystemMessage sm;
		if (_response == 1)
		{
			boolean newCc = false;
			if (!requestor.getParty().isInCommandChannel())
			{
				new L2CommandChannel(requestor); // Create new CC
				newCc = true;
			}
			requestor.getParty().getCommandChannel().addParty(player.getParty());
			if (!newCc)
			{
				sm = SystemMessageId.JOINED_COMMAND_CHANNEL.getSystemMessage();
				player.getParty().broadcastToPartyMembers(sm);
			}
		}
		else
		{
			sm = new SystemMessage(SystemMessageId.S1_DECLINED_CHANNEL_INVITATION);
			sm.addString(player.getName());
			requestor.sendPacket(sm);
		}
		sm = null;
		
		sendPacket(ActionFailed.STATIC_PACKET);
		player.setActiveRequester(null);
		requestor.onTransactionResponse();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_0E_REQUESTEXASKJOINMPCC;
	}
}
