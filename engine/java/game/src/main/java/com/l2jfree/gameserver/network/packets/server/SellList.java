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
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * This class ...
 * 
 * @version $Revision: 1.4.2.3.2.4 $ $Date: 2005/03/27 15:29:39 $
 */
public class SellList extends L2ServerPacket
{
	private static final String _S__10_SELLLIST = "[S] 10 SellList";
	
	private final L2Player _activeChar;
	private final L2MerchantInstance _lease;
	private final long _money;
	private final List<L2ItemInstance> _selllist = new ArrayList<L2ItemInstance>();
	
	public SellList(L2Player player)
	{
		_activeChar = player;
		_lease = null;
		_money = _activeChar.getAdena();
		doLease();
	}
	
	public SellList(L2Player player, L2MerchantInstance lease)
	{
		_activeChar = player;
		_lease = lease;
		_money = _activeChar.getAdena();
		doLease();
	}
	
	private void doLease()
	{
		if (_lease == null)
		{
			for (L2ItemInstance item : _activeChar.getInventory().getItems())
			{
				if (!item.isEquipped() // Not equipped
						&& item.isSellable() // Item is sellable
						&& (_activeChar.getPet() == null // Pet not summoned or
						|| item.getObjectId() != _activeChar.getPet().getControlItemId())) // Pet is summoned and not the item that summoned the pet
				{
					_selllist.add(item);
					if (_log.isDebugEnabled())
						_log.info("item added to selllist: " + item.getItem().getName());
				}
			}
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x06);
		writeCompQ(_money);
		writeD(0x00);
		writeH(_selllist.size());
		
		for (L2ItemInstance item : _selllist)
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
			writeCompQ(item.getItem().getReferencePrice() / 2);
			
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
		return _S__10_SELLLIST;
	}
}
