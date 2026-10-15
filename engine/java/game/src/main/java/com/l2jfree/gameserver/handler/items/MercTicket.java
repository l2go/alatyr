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
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;

public class MercTicket implements IItemHandler
{
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		L2Player player = playable.getActingPlayer();
		MercTicketManager.getInstance().reqPosition(player, item);
	}
	
	@Override
	public int[] getItemIds()
	{
		return MercTicketManager.getInstance().getItemIds();
	}
}
