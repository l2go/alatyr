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

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.FactSink;
import com.l2jfree.gameserver.network.bridge.SemanticPacket;

import java.util.List;

import com.l2jfree.Config;
import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class BuyList extends L2ServerPacket implements SemanticPacket
{
	@Override
	public void describe(FactSink sink, com.l2jfree.gameserver.gameobjects.L2Player receiver)
	{
		if (_money < 0)
			throw new IllegalArgumentException("negative purse");
		var goods = new java.util.ArrayList<com.l2jfree.gameserver.network.bridge.BridgeValue>();
		for (L2ItemInstance item : _list)
		{
			long count = item.getCount();
			if (count != -1 && count <= 0)
				continue;
			double price = item.getItemId() >= 3960 && item.getItemId() <= 4026
					? item.getPriceToSell() * Config.RATE_SIEGE_GUARDS_PRICE * _taxRate
					: item.getPriceToSell() * _taxRate;
			if (!Double.isFinite(price) || price < 0 || price >= 0x1p63)
				throw new IllegalArgumentException("unrepresentable price");
			goods.add(BridgeField.object(1, BridgeField.number(2, item.getObjectId()),
					BridgeField.number(3, item.getItemDisplayId()), BridgeField.number(4, count),
					BridgeField.number(5, (long)price)).value());
		}
		sink.emit(BridgeFact.of(12, BridgeField.number(22, _listId), BridgeField.number(23, _money),
				BridgeField.list(24, goods), BridgeField.number(74, sink.now())));
	}

	private static final String _S__07_BUYLIST = "[S] 07 BuyList [ddh (hdddhhdhhhdddddddd)]";
	private final int _listId;
	private final L2ItemInstance[] _list;
	private final long _money;
	private double _taxRate = 1.;
	
	public BuyList(L2TradeList list, long currentMoney)
	{
		_listId = list.getListId();
		List<L2ItemInstance> lst = list.getItems();
		_list = lst.toArray(new L2ItemInstance[lst.size()]);
		_money = currentMoney;
	}
	
	public BuyList(L2TradeList list, long currentMoney, double taxRate)
	{
		_listId = list.getListId();
		List<L2ItemInstance> lst = list.getItems();
		_list = lst.toArray(new L2ItemInstance[lst.size()]);
		_money = currentMoney;
		_taxRate = taxRate;
	}
	
	public BuyList(List<L2ItemInstance> lst, int listId, long currentMoney)
	{
		_listId = listId;
		_list = lst.toArray(new L2ItemInstance[lst.size()]);
		_money = currentMoney;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x07);
		// current money
		writeCompQ(_money);
		writeD(_listId);
		writeH(_list.length);
		
		for (L2ItemInstance item : _list)
		{
			if (item.getCount() > 0 || item.getCount() == -1)
			{
				writeH(item.getItem().getType1()); // item type1
				writeD(item.getObjectId());
				writeD(item.getItemDisplayId());
				writeCompQ(item.getCount() >= 0 ? item.getCount() : 0); // max amount of items that a player can buy at a time (with this itemid)
				writeH(item.getItem().getType2()); // item type2
				writeH(item.getCustomType1()); // custom type1
				writeD(item.getItem().getBodyPart());
				writeH(item.getEnchantLevel()); // enchant level
				writeH(item.getCustomType2()); // custom type2
				writeH(0x00);
				if (item.getItemId() >= 3960 && item.getItemId() <= 4026)//Config.RATE_SIEGE_GUARDS_PRICE-//'
					writeCompQ((long)(item.getPriceToSell() * Config.RATE_SIEGE_GUARDS_PRICE * _taxRate));
				else
					writeCompQ((long)(item.getPriceToSell() * _taxRate));
				writeElementalInfo(item);
			}
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
		return _S__07_BUYLIST;
	}
}
