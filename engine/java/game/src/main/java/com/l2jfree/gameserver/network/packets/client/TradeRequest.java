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

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SendTradeRequest;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 *
 * This class ...
 *
 * @version $Revision: 1.2.2.1.2.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class TradeRequest extends L2ClientPacket
{
	private static final String _C__TRADEREQUEST = "[C] 1A TradeRequest c[d]";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		if (Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			return;
		}
		
		L2Object obj = null;
		
		// Get object from target
		if (player.getTargetId() == _objectId)
			obj = player.getTarget();
		
		// Get object from world
		if (obj == null)
		{
			obj = L2World.getInstance().getPlayer(_objectId);
			//_log.warn("Player "+player.getName()+" requested trade from player from outside of his knownlist.");
		}
		
		if (!(obj instanceof L2Player) || obj.getObjectId() == player.getObjectId())
		{
			requestFailed(SystemMessageId.TARGET_IS_INCORRECT);
			return;
		}
		
		L2Player partner = (L2Player)obj;
		
		// cant trade with players from other instance except from multivers
		if (!player.isSameInstance(partner))
		{
			sendAF();
			return;
		}
		
		if (partner.isInOlympiadMode() || player.isInOlympiadMode())
		{
			requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
			return;
		}
		
		if (BlockList.isBlocked(partner, player))
		{
			requestFailed(new SystemMessage(SystemMessageId.C1_HAS_ADDED_YOU_TO_IGNORE_LIST).addCharName(partner));
			return;
		}
		
		if (player.getDistanceSq(partner) > 22500) // 150
		{
			requestFailed(SystemMessageId.TARGET_TOO_FAR);
			return;
		}
		
		// Alt game - Karma punishment
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_TRADE)
		{
			if (player.getKarma() > 0)
			{
				requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
				return;
			}
			else if (partner.getKarma() > 0)
			{
				requestFailed(SystemMessageId.CANT_TRADE_WITH_TARGET);
				return;
			}
		}
		
		if (player.getPrivateStoreType() != 0 || partner.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			return;
		}
		
		if (player.isProcessingTransaction())
		{
			if (_log.isDebugEnabled())
				_log.debug("already trading with someone");
			requestFailed(SystemMessageId.ALREADY_TRADING);
			return;
		}
		
		if (partner.isProcessingRequest() || partner.isProcessingTransaction())
		{
			if (_log.isDebugEnabled())
				_log.debug("transaction already in progress.");
			requestFailed(new SystemMessage(SystemMessageId.C1_IS_BUSY_TRY_LATER).addString(partner.getName()));
			return;
		}
		
		if (partner.getTradeRefusal())
		{
			requestFailed(SystemMessageId.CANT_TRADE_WITH_TARGET);
			return;
		}
		
		player.onTransactionRequest(partner);
		partner.sendPacket(new SendTradeRequest(player.getObjectId()));
		SystemMessage sm = new SystemMessage(SystemMessageId.REQUEST_C1_FOR_TRADE);
		sm.addString(partner.getName());
		player.sendPacket(sm);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__TRADEREQUEST;
	}
}
