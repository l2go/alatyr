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
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * This class is sent by the client when the player drags an item out of his to
 * pet's inventory.
 * 
 * @version $Revision: 1.3.2.1.2.5 $ $Date: 2005/03/29 23:15:33 $
 */
public class RequestGiveItemToPet extends L2ClientPacket
{
	private static final String REQUESTCIVEITEMTOPET__C__8B = "[C] 8B RequestGiveItemToPet";
	
	private int _objectId;
	private long _amount;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_amount = readCompQ();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		else if (!FloodProtector.tryPerformAction(player, Protected.TRANSACTION))
			return;
		
		if (!(player.getPet() instanceof L2PetInstance))
		{
			requestFailed(SystemMessageId.DONT_HAVE_PET);
			return;
		}
		else if (player.getActiveEnchantItem() != null)
		{
			requestFailed(SystemMessageId.TRY_AGAIN_LATER);
			return;
		}
		else if (Shutdown.isActionDisabled(DisableType.TRANSACTION))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		else if (Config.GM_DISABLE_TRANSACTION && player.getAccessLevel() >= Config.GM_TRANSACTION_MIN
				&& player.getAccessLevel() <= Config.GM_TRANSACTION_MAX)
		{
			requestFailed(SystemMessageId.ACCOUNT_CANT_TRADE_ITEMS);
			return;
		}
		// Alt game - Karma punishment
		else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_TRADE && player.getKarma() > 0)
		{
			sendAF();
			return;
		}
		
		else if (player.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.ITEMS_CANNOT_BE_DISCARDED_OR_DESTROYED_WHILE_OPERATING_PRIVATE_STORE_OR_WORKSHOP);
			return;
		}
		else if (player.isProcessingTransaction())
		{
			requestFailed(SystemMessageId.CANNOT_DISCARD_OR_DESTROY_ITEM_WHILE_TRADING);
			return;
		}
		
		L2PetInstance pet = (L2PetInstance)player.getPet();
		if (pet.isDead())
		{
			requestFailed(SystemMessageId.CANNOT_GIVE_ITEMS_TO_DEAD_PET);
			return;
		}
		
		if (_amount <= 0)
		{
			requestFailed(SystemMessageId.NOT_ENOUGH_ITEMS);
			return;
		}
		
		L2ItemInstance item = player.getInventory().getItemByObjectId(_objectId);
		if (!item.isDropable() || !item.isDestroyable() || !item.isTradeable() || item.isAugmented()
				|| (Config.ALT_STRICT_HERO_SYSTEM && item.isHeroItem()))
		{
			requestFailed(SystemMessageId.ITEM_NOT_FOR_PETS);
			return;
		}
		else if (!item.isAvailable(player, true, false))
		{
			requestFailed(SystemMessageId.PET_CANNOT_USE_ITEM);
			return;
		}
		
		else if (!pet.getInventory().validateCapacity(item))
		{
			requestFailed(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
			return;
		}
		else if (!pet.getInventory().validateWeight(item, _amount))
		{
			requestFailed(SystemMessageId.UNABLE_TO_PLACE_ITEM_YOUR_PET_IS_TOO_ENCUMBERED);
			return;
		}
		
		if (player.transferItem("Transfer", _objectId, _amount, pet.getInventory(), pet) == null)
			_log.warn("Invalid item transfer request: " + pet.getName() + "(pet) --> " + player.getName());
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return REQUESTCIVEITEMTOPET__C__8B;
	}
}
