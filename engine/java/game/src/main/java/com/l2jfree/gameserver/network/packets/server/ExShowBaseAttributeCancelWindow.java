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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class ExShowBaseAttributeCancelWindow extends L2ServerPacket
{
	private static final String _S__FE_74_EXCSHOWBASEATTRIBUTECANCELWINDOW =
			"[S] FE:74 ExShowBaseAttributeCancelWindow";
	
	private final L2ItemInstance[] _items;
	
	public ExShowBaseAttributeCancelWindow(L2Player player)
	{
		_items = player.getInventory().getElementItems();
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x74);
		writeD(_items.length);
		for (L2ItemInstance item : _items)
		{
			writeD(item.getObjectId());
			writeCompQ(getPrice(item));
		}
	}
	
	private long getPrice(L2ItemInstance item)
	{
		switch (item.getItem().getCrystalType())
		{
			case L2Item.CRYSTAL_S:
			{
				if (item.getItem() instanceof L2Weapon)
					return 50000;
				else
					return 40000;
			}
			case L2Item.CRYSTAL_S80:
			{
				if (item.getItem() instanceof L2Weapon)
					return 100000;
				else
					return 80000;
			}
			case L2Item.CRYSTAL_S84:
			{
				if (item.getItem() instanceof L2Weapon)
					return 200000;
				else
					return 160000;
			}
		}
		
		return 0;
	}
	
	@Override
	public String getType()
	{
		return _S__FE_74_EXCSHOWBASEATTRIBUTECANCELWINDOW;
	}
}
