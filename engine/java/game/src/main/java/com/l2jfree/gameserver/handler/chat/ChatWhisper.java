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
package com.l2jfree.gameserver.handler.chat;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IChatHandler;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 *
 * @author  Noctarius
 */
public class ChatWhisper implements IChatHandler
{
	private final SystemChatChannelId[] _chatTypes = { SystemChatChannelId.Chat_Tell };
	
	/**
	 * @see com.l2jfree.gameserver.handler.IChatHandler#getChatType()
	 */
	@Override
	public SystemChatChannelId[] getChatTypes()
	{
		return _chatTypes;
	}
	
	/**
	 * @see com.l2jfree.gameserver.handler.IChatHandler#useChatHandler(com.l2jfree.gameserver.gameobjects.L2Player.player.L2Player, com.l2jfree.gameserver.network.enums.SystemChatChannelId, java.lang.String)
	 */
	@Override
	public void useChatHandler(L2Player activeChar, String target, SystemChatChannelId chatType, String text)
	{
		L2Player receiver = L2World.getInstance().getPlayer(target);
		
		if (receiver != null)
		{
			if ((!receiver.getMessageRefusal() && !BlockList.isBlocked(receiver, activeChar)) || activeChar.isGM())
			{
				if (receiver.isInOfflineMode())
				{
					activeChar.sendMessage(receiver.getName() + " is in offline mode!");
					return;
				}
				if (Config.JAIL_DISABLE_CHAT && receiver.isInJail())
				{
					activeChar.sendMessage(receiver.getName() + " is currently in jail.");
					return;
				}
				if (receiver.isChatBanned())
				{
					activeChar.sendMessage(receiver.getName() + " is currently muted.");
					return;
				}
				receiver.sendPacket(new CreatureSay(activeChar.getObjectId(), chatType, activeChar.getName(), text));
				activeChar.sendPacket(new CreatureSay(activeChar.getObjectId(), chatType, "->" + receiver.getName(),
						text));
			}
			else
				activeChar.sendPacket(SystemMessageId.THE_PERSON_IS_IN_MESSAGE_REFUSAL_MODE);
		}
		else
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_NOT_ONLINE);
			sm.addString(target);
			activeChar.sendPacket(sm);
		}
	}
}
