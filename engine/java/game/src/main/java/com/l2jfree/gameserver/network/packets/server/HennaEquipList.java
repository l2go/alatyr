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

import java.util.Arrays;
import java.util.Comparator;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.HennaTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class HennaEquipList extends L2ServerPacket
{
	private static final String _S__EE_HennaEquipList = "[S] EE HennaEquipList";
	
	private final L2Player _player;
	private final L2Henna[] _hennas;
	
	public HennaEquipList(L2Player player)
	{
		_player = player;
		_hennas = HennaTreeTable.getInstance().getAvailableHenna(player).clone();
		
		Arrays.sort(_hennas, new Comparator<L2Henna>() {
			@Override
			public int compare(L2Henna o1, L2Henna o2)
			{
				return checkRequirements(o1).compareTo(checkRequirements(o2));
			}
		});
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xee);
		writeCompQ(_player.getAdena());
		writeD(3); //available equip slot
		writeD(_hennas.length);
		
		for (L2Henna element : _hennas)
		{
			int req = checkRequirements(element);
			// Player must have at least one dye in inventory
			// to be able to see the henna that can be applied with it.
			if (Config.ALT_SHOW_FULL_HENNA_LIST || req != 3)
			{
				writeD(element.getSymbolId()); //symbol ID
				writeD(element.getItemId()); //item ID of dye
				writeCompQ(element.getAmount()); //amount of dye required
				writeCompQ(element.getPrice()); //amount of adena required
			}
			else
			{
				writeD(0x00);
				writeD(0x00);
				writeCompQ(0x00);
				writeCompQ(0x00);
			}
			
			// Makes no difference in current gracia final client
			//writeD(req == 0); //meet the requirement(1) or not(0)
			writeD(0x01);
		}
	}
	
	private final Integer checkRequirements(L2Henna henna)
	{
		final L2ItemInstance hennaItem = _player.getInventory().getItemByItemId(henna.getItemId());
		
		if (hennaItem == null)
			return 3;
		
		if (hennaItem.getCount() < henna.getAmount())
			return 2;
		
		if (_player.getAdena() < henna.getPrice())
			return 1;
		
		return 0;
	}
	
	@Override
	public String getType()
	{
		return _S__EE_HennaEquipList;
	}
}
