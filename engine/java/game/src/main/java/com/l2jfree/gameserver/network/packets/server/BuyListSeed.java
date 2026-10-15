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

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author l3x
 */

public final class BuyListSeed extends L2ServerPacket
{
	private static final String _S__E9_BUYLISTSEED = "[S] E9 BuyListSeed [dd h (hdddhhd)]";
	
	private final int _manorId;
	private List<L2ItemInstance> _list = new ArrayList<L2ItemInstance>();
	private final long _money;
	
	public BuyListSeed(L2TradeList list, int manorId, long currentMoney)
	{
		_money = currentMoney;
		_manorId = manorId;
		_list = list.getItems();
	}
	
	@Override
	public void writeImpl()
	{
		writeC(0xE9);
		
		// current money
		writeCompQ(_money);
		writeD(_manorId); // manor id
		
		writeH(_list.size()); // list length
		
		for (L2ItemInstance item : _list)
		{
			writeH(0x04); // item->type1
			writeD(0x00); // objectId
			writeD(item.getItemDisplayId()); // item id
			writeCompQ(item.getCount()); // item count
			writeH(0x04); // item->type2
			writeH(0x00);
			writeCompQ(item.getPriceToSell()); // price
		}
	}
	
	@Override
	public String getType()
	{
		return _S__E9_BUYLISTSEED;
	}
}
