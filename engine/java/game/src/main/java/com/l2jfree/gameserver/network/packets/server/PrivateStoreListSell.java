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
 * @version $Revision: 1.2.2.3.2.6 $ $Date: 2005/03/27 15:29:57 $
 */
public class PrivateStoreListSell extends L2ServerPacket
{
	private static final String _S__B4_PRIVATESTORELISTSELL = "[S] 9b PrivateStoreListSell";
	private final int _objId;
	private final long _playerAdena;
	private final boolean _packageSale;
	private final TradeList.TradeItem[] _items;
	
	// player's private shop
	public PrivateStoreListSell(L2Player player, L2Player storePlayer)
	{
		_objId = storePlayer.getObjectId();
		_playerAdena = player.getAdena();
		_items = storePlayer.getSellList().getItems();
		_packageSale = storePlayer.getSellList().isPackaged();
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xa1);
		writeD(_objId);
		writeD(_packageSale ? 1 : 0);
		writeCompQ(_playerAdena);
		
		writeD(_items.length);
		for (TradeList.TradeItem item : _items)
		{
			writeD(item.getItem().getType2());
			writeD(item.getObjectId());
			writeD(item.getItem().getItemDisplayId());
			writeCompQ(item.getCount());
			writeH(0x00);
			writeH(item.getEnchant());
			writeH(item.getCustomType2());
			writeD(item.getItem().getBodyPart());
			writeCompQ(item.getPrice()); //your price
			writeCompQ(item.getItem().getReferencePrice()); //store price
			
			writeElementalInfo(item); //8x h or d
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__B4_PRIVATESTORELISTSELL;
	}
}
