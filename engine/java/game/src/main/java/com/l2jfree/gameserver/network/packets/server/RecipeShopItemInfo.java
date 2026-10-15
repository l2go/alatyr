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
 * ddddd
 * @version $Revision: 1.1.2.3.2.3 $ $Date: 2005/03/27 15:29:39 $
 */
public class RecipeShopItemInfo extends L2ServerPacket
{
	private static final String _S__DA_RecipeShopItemInfo = "[S] da RecipeShopItemInfo";
	private final L2Player _crafter;
	private final int _recipeId;
	
	public RecipeShopItemInfo(L2Player crafter, int recipeId)
	{
		_crafter = crafter;
		_recipeId = recipeId;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xe0);
		writeD(_crafter.getObjectId());
		writeD(_recipeId);
		writeD((int)_crafter.getStatus().getCurrentMp());
		writeD(_crafter.getMaxMp());
		writeD(0xffffffff);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__DA_RecipeShopItemInfo;
	}
}
