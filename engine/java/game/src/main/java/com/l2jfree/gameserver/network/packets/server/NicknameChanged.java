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
 *
 * @author  devScarlet
 */
public class NicknameChanged extends L2ServerPacket
{
	private static final String S_CC_NICKNAMECHANGED = "[S] cc NicknameChanged";
	private final String _title;
	private final int _objectId;
	
	public NicknameChanged(L2Player cha)
	{
		_objectId = cha.getObjectId();
		_title = cha.getTitle();
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xcc);
		writeD(_objectId);
		writeS(_title);
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_CC_NICKNAMECHANGED;
	}
	
}
