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
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 * sample 5F 01 00 00 00 format cdd
 */
public final class RequestAnswerFriendInvite extends L2ClientPacket
{
	private static final String _C__5F_REQUESTANSWERFRIENDINVITE = "[C] 5F RequestAnswerFriendInvite";
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		L2Player requestor = activeChar.getActiveRequester();
		if (requestor == null)
		{
			requestFailed(SystemMessageId.THE_USER_YOU_REQUESTED_IS_NOT_IN_GAME);
			return;
		}
		
		if (_response != 1)
		{
			requestor.sendPacket(SystemMessageId.FAILED_TO_INVITE_A_FRIEND);
			requestor.sendPacket(SystemMessageId.THE_PLAYER_IS_REJECTING_FRIEND_INVITATIONS);
		}
		else
			requestor.getFriendList().add(activeChar);
		sendPacket(ActionFailed.STATIC_PACKET);
		
		activeChar.setActiveRequester(null);
		requestor.onTransactionResponse();
	}
	
	@Override
	public String getType()
	{
		return _C__5F_REQUESTANSWERFRIENDINVITE;
	}
}
