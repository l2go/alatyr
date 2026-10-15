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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IChatHandler;
import com.l2jfree.gameserver.instancemanager.PetitionManager;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 *
 * @author  Noctarius
 */
public class ChatPetition implements IChatHandler
{
	private final SystemChatChannelId[] _chatTypes = { SystemChatChannelId.Chat_GM_Pet,
			SystemChatChannelId.Chat_User_Pet };
	
	/**
	 * @see com.l2jfree.gameserver.handler.IChatHandler#getChatTypes()
	 */
	@Override
	public SystemChatChannelId[] getChatTypes()
	{
		return _chatTypes;
	}
	
	/**
	 * @see com.l2jfree.gameserver.handler.IChatHandler#useChatHandler(com.l2jfree.gameserver.gameobjects.L2Player.player.L2Player, java.lang.String, com.l2jfree.gameserver.network.enums.SystemChatChannelId, java.lang.String)
	 */
	@Override
	public void useChatHandler(L2Player activeChar, String target, SystemChatChannelId chatType, String text)
	{
		//TODO: Maybe next time I port my Petition System, but there are a lot of changes to do about it. Is there some other guy, like to rewrite it?
		if (!PetitionManager.getInstance().isPlayerInConsultation(activeChar))
		{
			activeChar.sendPacket(SystemMessageId.YOU_ARE_NOT_IN_PETITION_CHAT);
			return;
		}
		
		PetitionManager.getInstance().sendActivePetitionMessage(activeChar, text);
	}
}
