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
package com.l2jfree.gameserver.model.restriction.global;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author savormix
 */
public final class InstanceEquipmentRestriction extends AbstractRestriction
{
	@Override
	public void instanceChanged(L2Player activeChar, int oldInstance, int newInstance)
	{
		for (int slot : L2ServerPacket.getPaperdollSlots(true))
		{
			L2ItemInstance ii = activeChar.getInventory().getPaperdollItem(slot);
			if (ii != null && !ii.getItem().checkCondition(activeChar, false))
				while (ii.isEquipped())
					activeChar.useEquippableItem(ii, true);
		}
	}
}
