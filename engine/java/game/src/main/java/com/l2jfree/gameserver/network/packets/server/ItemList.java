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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * 
 *
 * sample
 * 
 * 27
 * 00 00
 * 01 00        // item count
 * 
 * 04 00        // itemType1  0-weapon/ring/earring/necklace  1-armor/shield  4-item/questitem/adena
 * c6 37 50 40  // objectId
 * cd 09 00 00  // itemId
 * 05 00 00 00  // count
 * 05 00        // itemType2  0-weapon  1-shield/armor  2-ring/earring/necklace  3-questitem  4-adena  5-item
 * 00 00        // always 0 ??
 * 00 00        // equipped 1-yes
 * 00 00        // slot    0006-lr.ear  0008-neck  0030-lr.finger  0040-head  0080-??  0100-l.hand  0200-gloves  0400-chest  0800-pants  1000-feet  2000-??  4000-r.hand  8000-r.hand
 * 00 00        // always 0 ??
 * 00 00        // always 0 ??
 * 
 
 * format   h (h dddhhhh hh)    revision 377
 * format   h (h dddhhhd hh)    revision 415
 * format   h (h dddhhhd hhhhd)  729
 * 
 * @version $Revision: 1.4.2.1.2.4 $ $Date: 2005/03/27 15:29:57 $
 */
public class ItemList extends L2ServerPacket
{
	private static final String _S__11_ITEMLIST = "[S] 11 ItemList";
	private final L2ItemInstance[] _items;
	private final boolean _showWindow;
	
	public ItemList(L2Player cha, boolean showWindow)
	{
		_items = cha.getInventory().getItems();
		_showWindow = showWindow;
		// cha.sendPacket(new UserInfo(cha)); ?
		if (_log.isDebugEnabled())
		{
			showDebug();
		}
	}
	
	public ItemList(L2ItemInstance[] items, boolean showWindow)
	{
		_items = items;
		_showWindow = showWindow;
		if (_log.isDebugEnabled())
		{
			showDebug();
		}
	}
	
	private void showDebug()
	{
		for (L2ItemInstance temp : _items)
		{
			_log.info("item:" + temp.getItem().getName() + " type1:" + temp.getItem().getType1() + " type2:"
					+ temp.getItem().getType2());
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x11);
		writeH(_showWindow ? 0x01 : 0x00);
		
		int count = _items.length;
		writeH(count);
		
		for (L2ItemInstance temp : _items)
		{
			if (temp == null || temp.getItem() == null)
				continue;
			
			writeH(temp.getItem().getType1()); // item type1
			
			writeD(temp.getObjectId());
			writeD(temp.getItemDisplayId());
			// writeD(0x00);
			writeD(temp.getLocationSlot());
			writeCompQ(temp.getCount());
			writeH(temp.getItem().getType2()); // item type2
			writeH(temp.getCustomType1()); // item type3
			writeH(temp.isEquipped() ? 0x01 : 0x00);
			writeD(temp.getItem().getBodyPart());
			
			writeH(temp.getEnchantLevel()); // enchant level
			//race tickets
			writeH(temp.getCustomType2()); // item type3
			
			if (temp.isAugmented())
				writeD(temp.getAugmentation().getAugmentationId());
			else
				writeD(0x00);
			
			writeD(temp.getMana());
			
			// T1
			writeElementalInfo(temp);
			// T2
			writeD(temp.isTimeLimitedItem() ? (int)(temp.getRemainingTime() / 1000) : -1);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__11_ITEMLIST;
	}
}
