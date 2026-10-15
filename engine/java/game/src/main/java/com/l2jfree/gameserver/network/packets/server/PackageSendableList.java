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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author -Wooden-
 */
public class PackageSendableList extends L2ServerPacket
{
	private static final String _S__C3_PACKAGESENDABLELIST = "[S] C3 PackageSendableList";
	
	private final List<L2ItemInstance> _items;
	private final int _targetPlayerObjId;
	private final long _playerAdena;
	
	public PackageSendableList(L2Player sender, int playerOID)
	{
		_targetPlayerObjId = playerOID;
		_playerAdena = sender.getAdena();
		
		_items = new ArrayList<L2ItemInstance>();
		for (L2ItemInstance temp : sender.getInventory().getAvailableItems(true, false))
		{
			if (temp != null && temp.isDepositable(false))
				_items.add(temp);
		}
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xd2);
		
		writeD(_targetPlayerObjId);
		writeCompQ(_playerAdena);
		writeD(_items.size());
		for (L2ItemInstance item : _items) // format inside the for taken from SellList part use should be about the same
		{
			writeH(item.getItem().getType1());
			writeD(item.getObjectId());
			writeD(item.getItemDisplayId());
			writeCompQ(item.getCount());
			writeH(item.getItem().getType2());
			writeH(item.getCustomType1());
			writeD(item.getItem().getBodyPart());
			writeH(item.getEnchantLevel());
			writeH(0x00);
			writeH(item.getCustomType2());
			writeD(item.getObjectId()); // Will be used in RequestPackageSend response packet
			//T1
			writeElementalInfo(item); //8x h or d
		}
		_items.clear();
	}
	
	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__C3_PACKAGESENDABLELIST;
	}
}
