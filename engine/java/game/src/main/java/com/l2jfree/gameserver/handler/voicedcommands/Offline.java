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
package com.l2jfree.gameserver.handler.voicedcommands;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IVoicedCommandHandler;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;

public class Offline implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "offline" };
	
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (!Config.ALLOW_OFFLINE_TRADE)
			return false;
		
		switch (activeChar.getPrivateStoreType())
		{
			case L2Player.STORE_PRIVATE_MANUFACTURE:
			{
				if (!Config.ALLOW_OFFLINE_TRADE_CRAFT)
					break;
			}
			//$FALL-THROUGH$
			case L2Player.STORE_PRIVATE_SELL:
			case L2Player.STORE_PRIVATE_BUY:
			case L2Player.STORE_PRIVATE_PACKAGE_SELL:
			{
				if (activeChar.isInsideZone(L2Zone.FLAG_PEACE) || activeChar.isGM())
				{
					if (Config.OFFLINE_TRADE_PRICE > 0)
					{
						if (activeChar.getInventory().destroyItemByItemId("offlinetrade",
								Config.OFFLINE_TRADE_PRICE_ITEM, Config.OFFLINE_TRADE_PRICE, null, null) != null)
							activeChar.enterOfflineMode();
						else
							activeChar.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					}
					else
					{
						activeChar.enterOfflineMode();
					}
					return true;
				}
				else
				{
					activeChar.sendMessage("You must be in a peace zone to use offline mode!");
					return true;
				}
			}
		}
		
		activeChar.sendMessage("You must be in trade mode to use offline mode!");
		return true;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
