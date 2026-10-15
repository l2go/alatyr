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
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExDuelAskStart;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Format:(ch) Sd
 * @author  -Wooden-
 */
public final class RequestDuelStart extends L2ClientPacket
{
	private static final String _C__D0_27_REQUESTDUELSTART = "[C] D0:27 RequestDuelStart";
	
	private String _player;
	private int _partyDuel;
	
	@Override
	protected void readImpl()
	{
		_player = readS();
		_partyDuel = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Player targetChar = L2World.getInstance().getPlayer(_player);
		if (targetChar == null)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL);
			return;
		}
		else if (activeChar == targetChar)
		{
			requestFailed(SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL);
			return;
		}
		// Check if duel is possible
		else if (!activeChar.canDuel())
		{
			requestFailed(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME);
			return;
		}
		else if (!targetChar.canDuel())
		{
			requestFailed(targetChar.getNoDuelReason());
			return;
		}
		// Players may not be too far apart
		else if (!activeChar.isInsideRadius(targetChar, 250, false, false))
		{
			requestFailed(new SystemMessage(
					SystemMessageId.C1_CANNOT_RECEIVE_A_DUEL_CHALLENGE_BECAUSE_C1_IS_TOO_FAR_AWAY).addString(targetChar
					.getName()));
			return;
		}
		
		// Duel is a party duel
		if (_partyDuel == 1)
		{
			// Player must be in a party & the party leader
			if (!activeChar.isInParty() || !(activeChar.isInParty() && activeChar.getParty().isLeader(activeChar)))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			// Target must be in a party
			else if (!targetChar.isInParty())
			{
				requestFailed(SystemMessageId.CHALLENGED_PLAYER_NOT_IN_PARTY_CANNOT_PARTY_DUEL);
				return;
			}
			// Target may not be of the same party
			else if (activeChar.getParty().getPartyMembers().contains(targetChar))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			// Check if every player is ready for a duel
			for (L2Player temp : activeChar.getParty().getPartyMembers())
			{
				if (!temp.canDuel())
				{
					requestFailed(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME);
					return;
				}
			}
			L2Player partyLeader = null; // snatch party leader of targetChar's party
			for (L2Player temp : targetChar.getParty().getPartyMembers())
			{
				if (partyLeader == null)
					partyLeader = temp;
				
				if (!temp.canDuel())
				{
					requestFailed(SystemMessageId.THE_OPPOSING_PARTY_IS_CURRENTLY_UNABLE_TO_ACCEPT_A_CHALLENGE_TO_A_DUEL);
					return;
				}
			}
			
			// Send request to targetChar's party leader
			if (partyLeader != null)
			{
				if (!partyLeader.isProcessingRequest())
				{
					activeChar.onTransactionRequest(partyLeader);
					partyLeader.sendPacket(new ExDuelAskStart(activeChar.getName(), _partyDuel));
					
					if (_log.isDebugEnabled())
						_log.info(activeChar.getName() + " requested a duel with " + partyLeader.getName());
					
					SystemMessage msg = new SystemMessage(SystemMessageId.C1_PARTY_HAS_BEEN_CHALLENGED_TO_A_DUEL);
					msg.addString(partyLeader.getName());
					sendPacket(msg);
					
					msg = new SystemMessage(SystemMessageId.C1_PARTY_HAS_CHALLENGED_YOUR_PARTY_TO_A_DUEL);
					msg.addString(activeChar.getName());
					targetChar.sendPacket(msg);
					msg = null;
				}
				else
					sendPacket(new SystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER).addString(partyLeader.getName()));
			}
		}
		else
		// 1vs1 duel
		{
			if (!targetChar.isProcessingRequest())
			{
				activeChar.onTransactionRequest(targetChar);
				targetChar.sendPacket(new ExDuelAskStart(activeChar.getName(), _partyDuel));
				
				if (_log.isDebugEnabled())
					_log.info(activeChar.getName() + " requested a duel with " + targetChar.getName());
				
				SystemMessage msg = new SystemMessage(SystemMessageId.C1_HAS_BEEN_CHALLENGED_TO_A_DUEL);
				msg.addString(targetChar.getName());
				sendPacket(msg);
				
				msg = new SystemMessage(SystemMessageId.C1_HAS_CHALLENGED_YOU_TO_A_DUEL);
				msg.addString(activeChar.getName());
				targetChar.sendPacket(msg);
				msg = null;
			}
			else
				sendPacket(new SystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER).addString(targetChar.getName()));
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__D0_27_REQUESTDUELSTART;
	}
}
