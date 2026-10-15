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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.TradeOtherAdd;
import com.l2jfree.gameserver.network.packets.server.TradeOwnAdd;

/**
 * This class represents a packet that is sent by the client when you are adding an item
 * to the trade window.
 */
public class AddTradeItem extends L2ClientPacket
{
	private static final String _C__ADDTRADEITEM = "[C] 1B AddTradeItem c[ddq]";
	
	private int _tradeId;
	private int _objectId;
	private long _count;
	
	@Override
	protected void readImpl()
	{
		_tradeId = readD();
		_objectId = readD();
		_count = readCompQ();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			player.cancelActiveTrade();
			return;
		}
		
		TradeList trade = player.getActiveTradeList();
		if (trade == null)
		{
			_log.warn("Character: " + player.getName() + " requested item:" + _objectId
					+ " add without active tradelist:" + _tradeId);
			requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
			return;
		}
		
		L2Player partner = trade.getPartner();
		if (partner == null || L2World.getInstance().getPlayer(partner.getObjectId()) == null
				|| partner.getActiveTradeList() == null)
		{
			// Trade partner not found, cancel trade
			if (trade.getPartner() != null)
				_log.warn("Character:" + player.getName() + " requested invalid trade object: " + _objectId);
			requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			player.cancelActiveTrade();
			return;
		}
		
		if (Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			trade.getPartner().sendPacket(SystemMessageId.CANT_TRADE_WITH_TARGET);
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			player.cancelActiveTrade();
			return;
		}
		
		if (trade.isConfirmed() || trade.getPartner().getActiveTradeList().isConfirmed())
		{
			requestFailed(SystemMessageId.CANNOT_ADJUST_ITEMS_AFTER_TRADE_CONFIRMED);
			return;
		}
		
		if (!player.validateItemManipulation(_objectId, "trade") && !player.isGM())
		{
			requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
			return;
		}
		
		final TradeList.TradeItem item = trade.addItem(_objectId, _count);
		if (item != null)
		{
			sendPacket(new TradeOwnAdd(item));
			trade.getPartner().sendPacket(new TradeOtherAdd(item));
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__ADDTRADEITEM;
	}
}
