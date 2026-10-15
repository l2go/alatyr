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

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellEntry;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellIngredient;
import com.l2jfree.gameserver.datatables.MultisellTable.MultiSellListContainer;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class MultiSellList extends L2ServerPacket
{
	private static final String _S__D0_MULTISELLLIST = "[S] d0 MultiSellList";
	
	private final MultiSellListContainer _list;
	private final int _listId;
	private final int _page;
	private final int _finished;
	
	public MultiSellList(MultiSellListContainer list, int page, int finished)
	{
		_list = list;
		_listId = list.getListId();
		_page = page;
		_finished = finished;
	}
	
	@Override
	protected void writeImpl()
	{
		// [ddddd] [dchh] [hdhdh] [hhdh]
		
		writeC(0xd0);
		writeD(_listId); // list id
		writeD(_page); // page
		writeD(_finished); // finished
		writeD(0x28); // size of pages
		writeD(_list == null ? 0 : _list.getEntries().size()); //list length
		
		if (_list != null)
		{
			for (MultiSellEntry ent : _list.getEntries())
			{
				writeD(ent.getEntryId());
				writeC(ent.stackable());
				writeH(0x00); // C6
				writeD(0x00); // C6
				if (Config.PACKET_FINAL)
				{
					writeD(0x00); // T1
					writeH(65534); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
					writeH(0x00); // T1
				}
				else
				{
					writeD(-2); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
					writeD(0x00); // T1
				}
				writeH(ent.getProducts().size());
				writeH(ent.getIngredients().size());
				
				for (MultiSellIngredient i : ent.getProducts())
				{
					final int itemId = i.getItemId();
					final L2Item template = ItemTable.getInstance().getTemplate(itemId);
					
					if (template == null)
					{
						writeD(itemId);
						writeD(0x00);
						writeH(65535);
					}
					else
					{
						writeD(template.getItemDisplayId());
						writeD(template.getBodyPart());
						writeH(template.getType2());
					}
					
					writeCompQ(i.getItemCount());
					writeH(i.getEnchantmentLevel()); //enchtant lvl
					writeD(i.getAugmentId()); // C6
					writeD(i.getManaLeft()); // C6
					if (Config.PACKET_FINAL)
					{
						writeH(i.getElementId()); // T1 element id
						writeH(i.getElementVal()); // T1 element power
						writeH(i.getFireVal()); // T1 fire
						writeH(i.getWaterVal()); // T1 water
						writeH(i.getWindVal()); // T1 wind
						writeH(i.getEarthVal()); // T1 earth
						writeH(i.getHolyVal()); // T1 holy
						writeH(i.getDarkVal()); // T1 dark
					}
					else
					{
						writeD(-2); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
					}
				}
				
				for (MultiSellIngredient i : ent.getIngredients())
				{
					final int itemId = i.getItemId();
					final L2Item template = ItemTable.getInstance().getTemplate(itemId);
					
					if (template == null)
					{
						writeD(itemId); //ID
						writeH(65535);
					}
					else
					{
						writeD(template.getItemDisplayId()); //ID
						writeH(template.getType2());
					}
					
					writeCompQ(i.getItemCount()); //Count
					writeH(i.getEnchantmentLevel()); //Enchant Level
					writeD(i.getAugmentId()); // C6
					writeD(i.getManaLeft()); // C6
					if (Config.PACKET_FINAL)
					{
						writeH(i.getElementId()); // T1
						writeH(i.getElementVal()); // T1
						writeH(i.getFireVal()); // T1
						writeH(i.getWaterVal()); // T1
						writeH(i.getWindVal()); // T1
						writeH(i.getEarthVal()); // T1
						writeH(i.getHolyVal()); // T1
						writeH(i.getDarkVal()); // T1
					}
					else
					{
						writeD(-2); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
						writeD(0x00); // T1
					}
				}
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _S__D0_MULTISELLLIST;
	}
}
