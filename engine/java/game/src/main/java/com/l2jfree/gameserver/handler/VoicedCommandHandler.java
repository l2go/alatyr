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

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.voicedcommands.Auction;
import com.l2jfree.gameserver.handler.voicedcommands.Banking;
import com.l2jfree.gameserver.handler.voicedcommands.CastleDoors;
import com.l2jfree.gameserver.handler.voicedcommands.Hellbound;
import com.l2jfree.gameserver.handler.voicedcommands.Mail;
import com.l2jfree.gameserver.handler.voicedcommands.Offline;
import com.l2jfree.gameserver.handler.voicedcommands.Report;
import com.l2jfree.gameserver.handler.voicedcommands.VersionInfo;
import com.l2jfree.gameserver.handler.voicedcommands.Wedding;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.util.HandlerRegistry;

public final class VoicedCommandHandler extends HandlerRegistry<String, IVoicedCommandHandler>
{
	private static final class SingletonHolder
	{
		private static final VoicedCommandHandler INSTANCE = new VoicedCommandHandler();
	}
	
	public static VoicedCommandHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private VoicedCommandHandler()
	{
		registerVoicedCommandHandler(new Auction());
		registerVoicedCommandHandler(new Banking());
		registerVoicedCommandHandler(new CastleDoors());
		registerVoicedCommandHandler(new Hellbound());
		registerVoicedCommandHandler(new Mail());
		registerVoicedCommandHandler(new Offline());
		registerVoicedCommandHandler(new Report());
		registerVoicedCommandHandler(new VersionInfo());
		registerVoicedCommandHandler(new Wedding());
		
		_log.info("VoicedCommandHandler: Loaded " + size() + " handlers.");
	}
	
	@Override
	protected String standardizeKey(String key)
	{
		return key.trim().toLowerCase();
	}
	
	private void registerVoicedCommandHandler(IVoicedCommandHandler handler)
	{
		registerAll(handler, handler.getVoicedCommandList());
	}
	
	public boolean useVoicedCommand(String text, L2Player activeChar)
	{
		if (!text.startsWith(".") || text.length() < 2)
			return false;
		
		final StringTokenizer st = new StringTokenizer(text);
		
		final String command = st.nextToken().substring(1).toLowerCase(); // until the first space without the starting dot
		final String params;
		
		if (st.hasMoreTokens())
			params = text.substring(command.length() + 2).trim(); // the rest
		else if (activeChar.getTarget() != null)
			params = activeChar.getTarget().getName();
		else
			params = "";
		
		if (GlobalRestrictions.useVoicedCommand(command, activeChar, params))
			return true;
		
		final IVoicedCommandHandler handler = get(command);
		
		if (handler != null)
			return handler.useVoicedCommand(command, activeChar, params);
		
		return false;
	}
}
