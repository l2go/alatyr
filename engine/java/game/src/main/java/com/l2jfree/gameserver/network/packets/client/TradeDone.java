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

/**
 * This class ...
 * 
 * @version $Revision: 1.6.2.2.2.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class TradeDone extends L2ClientPacket
{
	private static final String _C__TRADEDONE = "[C] 1C TradeDone c[d]";
	
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			player.cancelActiveTrade();
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		TradeList trade = player.getActiveTradeList();
		if (trade == null)
		{
			if (_log.isDebugEnabled())
				_log.warn("player.getTradeList == null in " + getType() + " for player " + player.getName());
			requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
			return;
		}
		if (trade.isLocked())
			return;
		
		if (_response == 1)
		{
			if (trade.getPartner() == null || L2World.getInstance().getPlayer(trade.getPartner().getObjectId()) == null)
			{
				// Trade partner not found, cancel trade
				player.cancelActiveTrade();
				requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
				return;
			}
			
			if (trade.getOwner().getActiveEnchantItem() != null || trade.getPartner().getActiveEnchantItem() != null)
			{
				requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
				return;
			}
			
			if (Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
					&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
			{
				player.cancelActiveTrade();
				requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
				return;
			}
			
			if (!player.isSameInstance(trade.getPartner()))
			{
				player.cancelActiveTrade();
				requestFailed(SystemMessageId.TRADE_ATTEMPT_FAILED);
				return;
			}
			
			trade.confirm();
		}
		else
			player.cancelActiveTrade();
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__TRADEDONE;
	}
}
