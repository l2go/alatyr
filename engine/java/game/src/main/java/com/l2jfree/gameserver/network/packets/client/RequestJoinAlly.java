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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.AskJoinAlly;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class represents a packet sent by the client when a player requests alliance with
 * another clan.
 * 
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestJoinAlly extends L2ClientPacket
{
	private static final String _C__82_REQUESTJOINALLY = "[C] 82 RequestJoinAlly";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getClan() == null)
		{
			//requestFailed(SystemMessageId.YOU_ARE_NOT_A_CLAN_MEMBER);
			requestFailed(SystemMessageId.FEATURE_ONLY_FOR_ALLIANCE_LEADER);
			return;
		}
		
		L2Object obj = null;
		// Get object from target
		if (activeChar.getTargetId() == _objectId)
			obj = activeChar.getTarget();
		// Try to get object from world
		if (obj == null)
			obj = L2World.getInstance().getPlayer(_objectId);
		
		if (!(obj instanceof L2Player))
		{
			requestFailed(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return;
		}
		
		L2Player target = (L2Player)obj;
		if (!L2Clan.checkAllyJoinCondition(activeChar, target) || !activeChar.getRequest().setRequest(target, this))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		SystemMessage sm = new SystemMessage(SystemMessageId.S2_ALLIANCE_LEADER_OF_S1_REQUESTED_ALLIANCE);
		sm.addString(activeChar.getClan().getAllyName());
		sm.addString(activeChar.getName());
		target.sendPacket(sm);
		target.sendPacket(new AskJoinAlly(activeChar.getObjectId(), activeChar.getClan().getAllyName()));
		sendPacket(SystemMessageId.YOU_INVITED_FOR_ALLIANCE);
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__82_REQUESTJOINALLY;
	}
}
