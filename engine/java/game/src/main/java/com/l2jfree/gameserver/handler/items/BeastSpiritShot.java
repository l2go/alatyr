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
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author Tempy
 */
public final class BeastSpiritShot implements IItemHandler
{
	private static final int[] ITEM_IDS = { 6646, 6647, 20333, 20334 };
	
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (playable instanceof L2Summon)
		{
			((L2Summon)playable).getOwner().sendPacket(SystemMessageId.PET_CANNOT_USE_ITEM);
			return;
		}
		
		if (playable instanceof L2Player)
		{
			L2Player activeOwner = (L2Player)playable;
			L2Summon activePet = activeOwner.getPet();
			
			if (activePet == null)
			{
				activeOwner.sendPacket(SystemMessageId.PETS_ARE_NOT_AVAILABLE_AT_THIS_TIME);
				return;
			}
			
			if (item.getItemId() == 6647 || item.getItemId() == 20334)
				activePet.getShots().chargeBlessedSpiritshot(item);
			else
				activePet.getShots().chargeSpiritshot(item);
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
