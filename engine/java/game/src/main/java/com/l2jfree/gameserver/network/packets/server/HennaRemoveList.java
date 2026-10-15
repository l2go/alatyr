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
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class HennaRemoveList extends L2ServerPacket
{
	private static final String _S__E2_HennaRemoveList = "[S] ee HennaRemoveList";
	
	private L2Player _player;
	
	public HennaRemoveList(L2Player player)
	{
		_player = player;
	}
	
	private final int getHennaUsedSlots()
	{
		switch (_player.getHennaEmptySlots())
		{
			case 0:
				return 3;
			case 1:
				return 2;
			case 2:
				return 1;
				//case 3: return 0;
			default:
				return 0;
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xe6);
		writeCompQ(_player.getAdena());
		writeD(0x00);
		writeD(getHennaUsedSlots());
		
		for (int i = 1; i <= 3; i++)
		{
			L2Henna henna = _player.getHenna(i);
			if (henna != null)
			{
				writeD(henna.getSymbolId());
				writeD(henna.getItemId());
				writeD(henna.getAmount() / 2);
				writeD(0x00);
				writeD(henna.getPrice() / 5);
				writeD(0x00);
				writeD(0x01);
			}
		}
	}
	
	@Override
	public String getType()
	{
		return _S__E2_HennaRemoveList;
	}
}
