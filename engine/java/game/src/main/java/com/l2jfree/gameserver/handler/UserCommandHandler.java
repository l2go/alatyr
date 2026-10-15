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

import com.l2jfree.gameserver.handler.usercommands.Birthday;
import com.l2jfree.gameserver.handler.usercommands.ChannelDelete;
import com.l2jfree.gameserver.handler.usercommands.ChannelLeave;
import com.l2jfree.gameserver.handler.usercommands.ChannelListUpdate;
import com.l2jfree.gameserver.handler.usercommands.ClanPenalty;
import com.l2jfree.gameserver.handler.usercommands.ClanWarsList;
import com.l2jfree.gameserver.handler.usercommands.DisMount;
import com.l2jfree.gameserver.handler.usercommands.Escape;
import com.l2jfree.gameserver.handler.usercommands.FatigueTime;
import com.l2jfree.gameserver.handler.usercommands.GraduateList;
import com.l2jfree.gameserver.handler.usercommands.InstanceZone;
import com.l2jfree.gameserver.handler.usercommands.Loc;
import com.l2jfree.gameserver.handler.usercommands.Mount;
import com.l2jfree.gameserver.handler.usercommands.OlympiadStat;
import com.l2jfree.gameserver.handler.usercommands.PartyInfo;
import com.l2jfree.gameserver.handler.usercommands.SiegeStatus;
import com.l2jfree.gameserver.handler.usercommands.Time;
import com.l2jfree.util.NumberHandlerRegistry;

public final class UserCommandHandler extends NumberHandlerRegistry<IUserCommandHandler>
{
	public static UserCommandHandler getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private UserCommandHandler()
	{
		registerUserCommandHandler(new Birthday());
		registerUserCommandHandler(new ChannelDelete());
		registerUserCommandHandler(new ChannelLeave());
		registerUserCommandHandler(new ChannelListUpdate());
		registerUserCommandHandler(new ClanPenalty());
		registerUserCommandHandler(new ClanWarsList());
		registerUserCommandHandler(new DisMount());
		registerUserCommandHandler(new Escape());
		registerUserCommandHandler(new FatigueTime());
		registerUserCommandHandler(new GraduateList());
		registerUserCommandHandler(new InstanceZone());
		registerUserCommandHandler(new Loc());
		registerUserCommandHandler(new Mount());
		registerUserCommandHandler(new OlympiadStat());
		registerUserCommandHandler(new PartyInfo());
		registerUserCommandHandler(new SiegeStatus());
		registerUserCommandHandler(new Time());
		
		_log.info("UserCommandHandler: Loaded " + size() + " handlers.");
	}
	
	private void registerUserCommandHandler(IUserCommandHandler handler)
	{
		registerAll(handler, handler.getUserCommandList());
	}
	
	public IUserCommandHandler getUserCommandHandler(int userCommand)
	{
		return get(userCommand);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final UserCommandHandler _instance = new UserCommandHandler();
	}
}
