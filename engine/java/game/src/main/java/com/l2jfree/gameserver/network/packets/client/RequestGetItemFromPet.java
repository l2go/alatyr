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
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * This class represents a packet that is sent by the client when a player drags
 * an item from the pet to own inventory.
 * 
 * @version $Revision: 1.3.4.4 $ $Date: 2005/03/29 23:15:33 $
 */
public class RequestGetItemFromPet extends L2ClientPacket
{
	private static final String REQUESTGETITEMFROMPET__C__8C = "[C] 8C RequestGetItemFromPet";
	
	private int _objectId;
	private long _amount;
	@SuppressWarnings("unused")
	private int _unknown;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_amount = readCompQ();
		_unknown = readD();// = 0 for most trades
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
		
		L2PetInstance pet = (L2PetInstance)player.getPet();
		
		if (_amount > 0 && pet.transferItem("Transfer", _objectId, _amount, player.getInventory(), player, pet) == null)
			_log.warn("Invalid item transfer request: " + pet.getName() + "(pet) --> " + player.getName());
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return REQUESTGETITEMFROMPET__C__8C;
	}
}
