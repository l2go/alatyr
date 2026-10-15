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
import com.l2jfree.gameserver.network.packets.server.AskJoinPledge;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class RequestJoinPledge extends L2ClientPacket
{
	private static final String _C__24_REQUESTJOINPLEDGE = "[C] 24 RequestJoinPledge";
	
	private int _objectId;
	private int _pledgeType;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_pledgeType = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Clan clan = activeChar.getClan();
		if (clan == null || !L2Clan.checkPrivileges(activeChar, L2Clan.CP_CL_JOIN_CLAN))
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		L2Object obj;
		if (activeChar.getTargetId() == _objectId)
			obj = activeChar.getTarget();
		else
			obj = L2World.getInstance().getPlayer(_objectId);
		
		if (obj == null)
		{
			requestFailed(SystemMessageId.FIRST_SELECT_USER_TO_INVITE_TO_CLAN);
			return;
		}
		
		L2Player target = obj.getActingPlayer();
		if (!clan.checkClanJoinCondition(activeChar, target, _pledgeType))
		{
			sendAF();
			return;
		}
		
		if (!activeChar.getRequest().setRequest(target, this))
		{
			sendAF();
			return;
		}
		
		String _subPledge =
				(activeChar.getClan().getSubPledge(_pledgeType) != null ? activeChar.getClan()
						.getSubPledge(_pledgeType).getName() : null);
		target.sendPacket(new AskJoinPledge(activeChar.getObjectId(), _subPledge, _pledgeType, clan.getName()));
		sendPacket(new SystemMessage(SystemMessageId.INVITED_C1_TO_CLAN).addPcName(target));
		
		sendAF();
	}
	
	public int getSubPledgeType()
	{
		return _pledgeType;
	}
	
	@Override
	public String getType()
	{
		return _C__24_REQUESTJOINPLEDGE;
	}
}
