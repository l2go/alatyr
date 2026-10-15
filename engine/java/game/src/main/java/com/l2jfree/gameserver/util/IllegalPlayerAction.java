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
package com.l2jfree.gameserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author luisantonioa
 */
public final class IllegalPlayerAction implements Runnable
{
	private static final Logger _logAudit = LoggerFactory.getLogger("audit");
	
	protected String _message;
	protected int _punishment;
	protected L2Player _actor;
	
	public static final int PUNISH_BROADCAST = 1;
	public static final int PUNISH_KICK = 2;
	public static final int PUNISH_KICKBAN = 3;
	public static final int PUNISH_JAIL = 4;
	
	public IllegalPlayerAction(L2Player actor, String message, int punishment)
	{
		_message = message;
		_punishment = punishment;
		_actor = actor;
		
		switch (punishment)
		{
			case PUNISH_KICK:
				_actor.sendPacket(SystemMessageId.DISCONNECTED_AS_ILLEGAL_USER);
				break;
			case PUNISH_KICKBAN:
				_actor.sendPacket(SystemMessageId.ACCOUNT_SUSPENDED);
				break;
			case PUNISH_JAIL:
				_actor.sendPacket(SystemMessageId.BLOCKED_DUE_TO_3RD_PARTY_PROGRAM);
				break;
		}
	}
	
	@Override
	public void run()
	{
		_logAudit.info("AUDIT:" + _message + "," + _actor + " " + _punishment);
		
		GmListTable.broadcastMessageToGMs(_message);
		
		switch (_punishment)
		{
			case PUNISH_BROADCAST:
				return;
				
			case PUNISH_KICKBAN:
				_actor.setAccountAccesslevel(-100);
				//$FALL-THROUGH$
			case PUNISH_KICK:
				new Disconnection(_actor).defaultSequence(false);
				break;
			case PUNISH_JAIL:
				long duration = Config.DEFAULT_PUNISH_PARAM * 60000;
				
				if (_actor.isInJail())
					duration = Math.max(duration, _actor.getJailTimer());
				
				_actor.setInJail(true, (int)Math.ceil(duration / 60000.0));
				break;
		}
	}
}
