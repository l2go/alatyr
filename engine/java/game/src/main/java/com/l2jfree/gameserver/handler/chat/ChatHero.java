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
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 *
 * @author  Noctarius
 */
public class ChatHero implements IChatHandler
{
	private final SystemChatChannelId[] _chatTypes = { SystemChatChannelId.Chat_Hero };
	
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
		boolean canSpeak = activeChar.isGM();
		
		if (!canSpeak)
		{
			if (activeChar.isHero())
			{
				if (FloodProtector.tryPerformAction(activeChar, Protected.HEROVOICE))
				{
					canSpeak = true;
				}
				else
				{
					activeChar
							.sendMessage("Action failed. Heroes are only able to speak in the global channel once every 10 seconds.");
				}
			}
		}
		
		if (canSpeak)
		{
			String name =
					(activeChar.isGM() && Config.GM_NAME_HAS_BRACELETS) ? "[GM]" + activeChar.getName() : activeChar
							.getName();
			
			CreatureSay cs = new CreatureSay(activeChar.getObjectId(), chatType, name, text);
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				player.sendPacket(cs);
			}
		}
	}
}
