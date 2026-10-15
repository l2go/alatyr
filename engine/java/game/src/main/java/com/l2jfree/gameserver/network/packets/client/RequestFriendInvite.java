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
import com.l2jfree.gameserver.network.packets.server.FriendAddRequest;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public final class RequestFriendInvite extends L2ClientPacket
{
	private static final String _C__5E_REQUESTFRIENDINVITE = "[C] 5E RequestFriendInvite";
	
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		L2Player friend = L2World.getInstance().getPlayer(_name);
		if (friend == null || (friend.getAppearance().isInvisible() && friend.isGM()))
			sendPacket(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
		else if (friend == activeChar)
			sendPacket(SystemMessageId.YOU_CANNOT_ADD_YOURSELF_TO_OWN_FRIEND_LIST);
		else if (activeChar.getFriendList().contains(friend))
			sendPacket(new SystemMessage(SystemMessageId.C1_ALREADY_ON_LIST).addPcName(friend));
		else if (friend.isProcessingRequest())
			sendPacket(new SystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER).addPcName(friend));
		else
		{
			activeChar.onTransactionRequest(friend);
			
			friend.sendPacket(new SystemMessage(SystemMessageId.C1_REQUESTED_TO_BECOME_FRIENDS).addPcName(activeChar));
			friend.sendPacket(new FriendAddRequest(activeChar.getName()));
			sendPacket(new SystemMessage(SystemMessageId.REQUESTED_C1_ON_FRIENDS_LIST).addPcName(friend));
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__5E_REQUESTFRIENDINVITE;
	}
}
