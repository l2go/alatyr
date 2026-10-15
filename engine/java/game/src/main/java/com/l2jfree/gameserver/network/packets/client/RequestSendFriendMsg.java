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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.L2FriendSay;

/**
 * Receive Private (Friend) Message - 0xCC
 * 
 * Format: c SS
 * 
 * S: Message
 * S: Receiving Player
 * 
 * @author Tempy
 * 
 */
public class RequestSendFriendMsg extends L2ClientPacket
{
	private static final String _C__CC_REQUESTSENDMSG = "[C] CC RequestSendMsg";
	private static Logger _logChat = LoggerFactory.getLogger("chat");
	
	private String _message;
	private String _receiver;
	
	@Override
	protected void readImpl()
	{
		_message = readS();
		_receiver = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Player targetPlayer = L2World.getInstance().getPlayer(_receiver);
		if (targetPlayer == null)
		{
			requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			return;
		}
		
		if (Config.LOG_CHAT)
			_logChat.info("PRIV_MSG" + "[" + activeChar.getName() + " to " + _receiver + "]" + _message);
		
		targetPlayer.sendPacket(new L2FriendSay(activeChar.getName(), _receiver, _message));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__CC_REQUESTSENDMSG;
	}
}
