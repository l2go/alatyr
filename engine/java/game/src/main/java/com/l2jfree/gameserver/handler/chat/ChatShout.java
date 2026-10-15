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
import com.l2jfree.Config.ChatMode;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IChatHandler;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.mapregion.L2MapRegion;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 *
 * @author  Noctarius
 */
public class ChatShout implements IChatHandler
{
	private final SystemChatChannelId[] _chatTypes = { SystemChatChannelId.Chat_Shout };
	
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
		if (!FloodProtector.tryPerformAction(activeChar, Protected.GLOBAL_CHAT) && !activeChar.isGM())
		{
			activeChar.sendMessage("Flood protection: Using global chat failed.");
			return;
		}
		
		String name =
				(activeChar.isGM() && Config.GM_NAME_HAS_BRACELETS) ? "[GM]" + activeChar.getName() : activeChar
						.getName();
		CreatureSay cs = new CreatureSay(activeChar.getObjectId(), chatType, name, text);
		
		if (Config.DEFAULT_GLOBAL_CHAT == ChatMode.REGION)
		{
			L2MapRegion region =
					MapRegionManager.getInstance().getRegion(activeChar.getX(), activeChar.getY(), activeChar.getZ());
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				if (region == MapRegionManager.getInstance().getRegion(player.getX(), player.getY(), player.getZ())
						&& !(Config.REGION_CHAT_ALSO_BLOCKED && BlockList.isBlocked(player, activeChar))
						&& player.isSameInstance(activeChar))
				{
					player.sendPacket(cs);
				}
			}
		}
		else if (Config.DEFAULT_GLOBAL_CHAT == ChatMode.GLOBAL || Config.DEFAULT_GLOBAL_CHAT == ChatMode.GM
				&& activeChar.isGM())
		{
			for (L2Player player : L2World.getInstance().getAllPlayers())
			{
				if (!(Config.REGION_CHAT_ALSO_BLOCKED && BlockList.isBlocked(player, activeChar))
						&& player.isSameInstance(activeChar))
				{
					player.sendPacket(cs);
				}
			}
		}
	}
}
