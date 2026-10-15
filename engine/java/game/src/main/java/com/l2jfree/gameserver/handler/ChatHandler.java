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
package com.l2jfree.gameserver.handler;

import com.l2jfree.gameserver.handler.chat.ChatAll;
import com.l2jfree.gameserver.handler.chat.ChatAlliance;
import com.l2jfree.gameserver.handler.chat.ChatAnnounce;
import com.l2jfree.gameserver.handler.chat.ChatClan;
import com.l2jfree.gameserver.handler.chat.ChatCommander;
import com.l2jfree.gameserver.handler.chat.ChatHero;
import com.l2jfree.gameserver.handler.chat.ChatParty;
import com.l2jfree.gameserver.handler.chat.ChatPartyRoom;
import com.l2jfree.gameserver.handler.chat.ChatPetition;
import com.l2jfree.gameserver.handler.chat.ChatShout;
import com.l2jfree.gameserver.handler.chat.ChatSystem;
import com.l2jfree.gameserver.handler.chat.ChatTrade;
import com.l2jfree.gameserver.handler.chat.ChatWhisper;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.util.EnumHandlerRegistry;

/**
 * @author Noctarius
 */
public final class ChatHandler extends EnumHandlerRegistry<SystemChatChannelId, IChatHandler>
{
	public static ChatHandler getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private ChatHandler()
	{
		super(SystemChatChannelId.class);
		
		registerChatHandler(new ChatAll());
		registerChatHandler(new ChatAlliance());
		registerChatHandler(new ChatAnnounce());
		registerChatHandler(new ChatClan());
		registerChatHandler(new ChatCommander());
		registerChatHandler(new ChatHero());
		registerChatHandler(new ChatParty());
		registerChatHandler(new ChatPartyRoom());
		registerChatHandler(new ChatPetition());
		registerChatHandler(new ChatShout());
		registerChatHandler(new ChatSystem());
		registerChatHandler(new ChatTrade());
		registerChatHandler(new ChatWhisper());
		
		_log.info("ChatHandler: Loaded " + size() + " handlers.");
	}
	
	public void registerChatHandler(IChatHandler handler)
	{
		registerAll(handler, handler.getChatTypes());
	}
	
	public IChatHandler getChatHandler(SystemChatChannelId chatId)
	{
		return get(chatId);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final ChatHandler _instance = new ChatHandler();
	}
}
