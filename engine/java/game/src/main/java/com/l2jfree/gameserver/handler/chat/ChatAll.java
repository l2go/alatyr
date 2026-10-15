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
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;

/**
 *
 * @author  Noctarius
 */
public class ChatAll implements IChatHandler
{
	private final SystemChatChannelId[] _chatTypes = { SystemChatChannelId.Chat_Normal };
	
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
		String name =
				(activeChar.isGM() && Config.GM_NAME_HAS_BRACELETS) ? "[GM]"
						+ activeChar.getAppearance().getVisibleName() : activeChar.getAppearance().getVisibleName();
		
		CreatureSay cs = new CreatureSay(activeChar.getObjectId(), chatType, name, text);
		
		for (L2Player player : activeChar.getKnownList().getKnownPlayers().values())
		{
			if (player != null && activeChar.isInsideRadius(player, 1250, false, true)
					&& !(Config.REGION_CHAT_ALSO_BLOCKED && BlockList.isBlocked(player, activeChar)))
			{
				player.sendPacket(cs);
			}
		}
		activeChar.sendPacket(cs);
	}
}
