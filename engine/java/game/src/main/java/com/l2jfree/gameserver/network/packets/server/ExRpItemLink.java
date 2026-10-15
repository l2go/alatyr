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

import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 *
 * @author  KenM
 */
public final class ExRpItemLink extends L2ServerPacket
{
	private final static String S_FE_6C_EXPRPITEMLINK = "[S] FE:6C ExRpItemLink";
	private final L2ItemInstance _item;
	
	public ExRpItemLink(L2ItemInstance item)
	{
		_item = item;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_FE_6C_EXPRPITEMLINK;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x6c);
		// guessing xD
		writeD(_item.getObjectId());
		writeD(_item.getItemDisplayId());
		writeCompQ(_item.getCount());
		writeH(_item.getItem().getType2());
		writeD(_item.getItem().getBodyPart());
		writeH(_item.getEnchantLevel());
		writeH(_item.getCustomType2()); // item type3
		writeH(0x00); // ??
		writeD(_item.isAugmented() ? _item.getAugmentation().getAugmentationId() : 0x00);
		writeD(_item.getMana());
		
		// T1
		writeElementalInfo(_item);
	}
}
