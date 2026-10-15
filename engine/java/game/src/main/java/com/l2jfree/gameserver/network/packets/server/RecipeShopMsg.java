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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.2.1.2.3 $ $Date: 2005/03/27 15:29:57 $
 */
public class RecipeShopMsg extends L2ServerPacket
{
	private static final String _S__DB_RecipeShopMsg = "[S] db RecipeShopMsg";
	private final L2Player _activeChar;
	
	public RecipeShopMsg(L2Player player)
	{
		_activeChar = player;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xe1);
		writeD(_activeChar.getObjectId());
		writeS(_activeChar.getCreateList().getStoreName());//_activeChar.getTradeList().getSellStoreName());
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__DB_RecipeShopMsg;
	}
}
