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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author -Wooden-
 */
public class ExFishingEnd extends L2ServerPacket
{
	private static final String _S__FE_1F_EXFISHINGEND = "[S] FE:1f ExFishingEnd [dc]";
	private final boolean _win;
	L2Creature _activeChar;
	
	public ExFishingEnd(boolean win, L2Player character)
	{
		_win = win;
		_activeChar = character;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x1f);
		
		writeD(_activeChar.getObjectId());
		writeC(_win ? 1 : 0);
	}
	
	@Override
	public String getType()
	{
		return _S__FE_1F_EXFISHINGEND;
	}
}
