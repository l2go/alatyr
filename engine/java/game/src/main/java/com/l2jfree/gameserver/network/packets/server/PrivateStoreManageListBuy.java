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
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2005/03/27 15:29:40 $
 */
public class PrivateStoreManageListBuy extends L2ServerPacket
{
	private static final String _S__D0_PRIVATESELLLISTBUY = "[S] b7 PrivateSellListBuy";
	private final int _objId;
	private final long _playerAdena;
	private final L2ItemInstance[] _itemList;
	private final TradeList.TradeItem[] _buyList;
	
	public PrivateStoreManageListBuy(L2Player player)
	{
		_objId = player.getObjectId();
		_playerAdena = player.getAdena();
		_itemList = player.getInventory().getUniqueItems(false, true);
		_buyList = player.getBuyList().getItems();
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xbd);
		//section 1
		writeD(_objId);
		writeCompQ(_playerAdena);
		
		//section2
		writeD(_itemList.length); // inventory items for potential buy
		for (L2ItemInstance item : _itemList)
		{
			writeD(item.getItemDisplayId());
			writeH(0); //show enchant lvl as 0, as you can't buy enchanted weapons
			writeCompQ(item.getCount());
			writeCompQ(item.getReferencePrice());
			writeH(0x00);
			writeD(item.getItem().getBodyPart());
			writeH(item.getItem().getType2());
			
			writeElementalInfo(item); //8x h or d
		}
		
		//section 3
		writeD(_buyList.length); //count for all items already added for buy
		for (TradeList.TradeItem item : _buyList)
		{
			writeD(item.getItem().getItemDisplayId());
			writeH(0);
			writeCompQ(item.getCount());
			writeCompQ(item.getItem().getReferencePrice());
			
			writeH(0x00);
			writeD(item.getItem().getBodyPart());
			writeH(item.getItem().getType2());
			writeCompQ(item.getPrice());//your price
			writeCompQ(item.getItem().getReferencePrice());//fixed store price
			
			writeElementalInfo(item); //8x h or d
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__D0_PRIVATESELLLISTBUY;
	}
}
