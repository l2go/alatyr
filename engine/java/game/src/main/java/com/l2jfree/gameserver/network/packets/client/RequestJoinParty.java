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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.AskJoinParty;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * sample 29 42 00 00 10 01 00 00 00
 * 
 * format cdd
 * 
 * 
 * @version $Revision: 1.7.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestJoinParty extends L2ClientPacket
{
	private static final String _C__29_REQUESTJOINPARTY = "[C] 29 RequestJoinParty";
	
	private String _name;
	private int _itemDistribution;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
		_itemDistribution = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player requestor = getClient().getActiveChar();
		if (requestor == null)
			return;
		
		L2Player target = L2World.getInstance().getPlayer(_name);
		if (target == null || (target.getAppearance().isInvisible() && !requestor.isGM()))
		{
			requestFailed(SystemMessageId.FIRST_SELECT_USER_TO_INVITE_TO_PARTY);
			return;
		}
		else if (target == requestor)
		{
			requestor.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return;
		}
		else if (BlockList.isBlocked(target, requestor))
		{
			requestFailed(new SystemMessage(SystemMessageId.C1_HAS_ADDED_YOU_TO_IGNORE_LIST).addCharName(target));
			return;
		}
		else if (target.isInParty())
		{
			requestFailed(new SystemMessage(SystemMessageId.C1_IS_ALREADY_IN_PARTY).addCharName(target));
			return;
		}
		else if (target.isInOfflineMode())
		{
			requestor.sendMessage("You can't invite " + target.getName() + " because the player is in offline mode!");
			return;
		}
		else if (!GlobalRestrictions.canInviteToParty(requestor, target))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		if (!requestor.isInParty())
		{
			if (!target.isProcessingRequest())
			{
				requestor.setParty(new L2Party(requestor, _itemDistribution));
				requestor.onTransactionRequest(target);
				target.sendPacket(new AskJoinParty(requestor.getName(), _itemDistribution));
				requestor.getParty().setPendingInvitation(true);
				
				if (_log.isDebugEnabled())
					_log.debug(requestor.getName() + "sent out a party invitation to:" + target.getName());
			}
			else
			{
				if (_log.isDebugEnabled())
					_log.warn(requestor.getName() + " already sent a party invitation");
				requestFailed(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return;
			}
		}
		else if (requestor.getParty().isInDimensionalRift())
		{
			requestFailed(SystemMessageId.NO_INVITE_PARTY_LOCKED);
			return;
		}
		else
		{
			L2Party invitor = requestor.getParty();
			if (!invitor.isLeader(requestor))
			{
				requestFailed(SystemMessageId.ONLY_LEADER_CAN_INVITE);
				return;
			}
			else if (invitor.getMemberCount() >= 9)
			{
				requestFailed(SystemMessageId.PARTY_FULL);
				return;
			}
			else if (invitor.getPendingInvitation())
			{
				requestFailed(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return;
			}
			
			if (!target.isProcessingRequest())
			{
				requestor.onTransactionRequest(target);
				target.sendPacket(new AskJoinParty(requestor.getName(), requestor.getParty().getLootDistribution()));
				requestor.getParty().setPendingInvitation(true);
				
				if (_log.isDebugEnabled())
					_log.debug(requestor.getName() + "sent out a party invitation to:" + target.getName());
			}
			else
			{
				if (_log.isDebugEnabled())
					_log.warn(target.getName() + " already has a party invitation");
				requestFailed(new SystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER).addCharName(target));
				return;
			}
		}
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__29_REQUESTJOINPARTY;
	}
}
