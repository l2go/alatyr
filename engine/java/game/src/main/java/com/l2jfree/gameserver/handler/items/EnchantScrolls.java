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

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.server.ChooseInventoryItem;

public class EnchantScrolls implements IItemHandler
{
	// All the item IDs that this handler knows.
	private static final int[] ITEM_IDS = {
			// A grade
			729, 730, 731, 732, 6569, 6570, 22009, 22013, 22015, 22017, 22019, 22021,
			// B grade
			947, 948, 949, 950, 6571, 6572, 22008, 22012, 22014, 22016, 22018, 22020,
			// C grade
			951, 952, 953, 954, 6573, 6574, 22007, 22011,
			// D grade
			955, 956, 957, 958, 6575, 6576, 22006, 22010,
			// S grade
			959, 960, 961, 962, 6577, 6578 };
	
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2Player))
			return;
		L2Player activeChar = (L2Player)playable;
		if (activeChar.isCastingNow())
			return;
		
		// Restrict enchant during restart/shutdown (because of an existing exploit)
		if (Shutdown.isActionDisabled(DisableType.ENCHANT))
		{
			activeChar.sendMessage("Enchanting items is not allowed during restart/shutdown.");
			return;
		}
		
		if (activeChar.getActiveEnchantItem() != null)
		{
			if (_log.isDebugEnabled())
				_log.warn(activeChar + " has got already an active enchant item");
			return;
		}
		
		activeChar.setActiveEnchantItem(item);
		//activeChar.sendPacket(SystemMessageId.SELECT_ITEM_TO_ENCHANT);
		
		int itemId = item.getItemId();
		
		if (Config.ALLOW_CRYSTAL_SCROLL && (itemId == 957 || itemId == 958 || itemId == 953 || itemId == 954 || // Crystal scrolls D and C Grades
				itemId == 949 || itemId == 950 || itemId == 731 || itemId == 732 || // Crystal scrolls B and A Grades
				itemId == 961 || itemId == 962)) // Crystal scrolls S Grade
			activeChar.sendPacket(new ChooseInventoryItem(itemId - 2));
		else
			activeChar.sendPacket(new ChooseInventoryItem(itemId));
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
