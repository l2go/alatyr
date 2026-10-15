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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * This class ...
 * 
 * @version $Revision: 1.7.2.2.2.3 $ $Date: 2005/03/27 15:29:39 $
 */
public class PrivateStoreListBuy extends L2ServerPacket
{
	//	private static final String _S__D1_PRIVATEBUYLISTBUY = "[S] b8 PrivateBuyListBuy";
	private static final String _S__BE_PRIVATESTORELISTBUY = "[S] be PrivateStoreListBuy";
	private final int _objId;
	private final long _playerAdena;
	private final TradeList.TradeItem[] _items;
	
	public PrivateStoreListBuy(L2Player player, L2Player storePlayer)
	{
		_objId = storePlayer.getObjectId();
		_playerAdena = player.getAdena();
		storePlayer.getSellList().updateItems(); // Update SellList for case inventory content has changed
		_items = storePlayer.getBuyList().getAvailableItems(player.getInventory());
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xbe);
		writeD(_objId);
		writeCompQ(_playerAdena);
		
		writeD(_items.length);
		
		for (TradeList.TradeItem item : _items)
		{
			writeD(item.getObjectId());
			writeD(item.getItem().getItemDisplayId());
			writeH(item.getEnchant());
			writeCompQ(item.getCount()); //give max possible sell amount
			writeCompQ(item.getItem().getReferencePrice());
			writeH(0);
			writeD(item.getItem().getBodyPart());
			writeH(item.getItem().getType2());
			writeCompQ(item.getPrice());//buyers price
			writeCompQ(item.getStoreCount()); // maximum possible tradecount
			
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
		return _S__BE_PRIVATESTORELISTBUY;
	}
}
