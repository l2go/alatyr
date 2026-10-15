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
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.network.packets.server.SSQStatus;

public class SevenSignsRecord implements IItemHandler
{
	// All the item IDs that this handler knows.
	private static final int[] ITEM_IDS = { SevenSigns.RECORD_SEVEN_SIGNS_ID };
	
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (playable instanceof L2Player)
		{
			L2Player player = playable.getActingPlayer();
			player.sendPacket(new SSQStatus(player, 1));
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
