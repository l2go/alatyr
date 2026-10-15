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
 * @author  KenM
 */
public class ExDuelUpdateUserInfo extends L2ServerPacket
{
	private static final String _S__FE_50_EXDUELUPDATEUSERINFO = "[S] FE:50 ExDuelUpdateUserInfo [sddddddddd]";
	private final L2Player _activeChar;
	
	public ExDuelUpdateUserInfo(L2Player cha)
	{
		_activeChar = cha;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x50);
		
		writeS(_activeChar.getName());
		writeD(_activeChar.getObjectId());
		writeD(_activeChar.getClassId().getId());
		writeD(_activeChar.getStat().getLevel());
		writeD((int)_activeChar.getStatus().getCurrentHp());
		writeD(_activeChar.getStat().getMaxHp());
		writeD((int)_activeChar.getStatus().getCurrentMp());
		writeD(_activeChar.getStat().getMaxMp());
		writeD((int)_activeChar.getStatus().getCurrentCp());
		writeD(_activeChar.getStat().getMaxCp());
	}
	
	@Override
	public String getType()
	{
		return _S__FE_50_EXDUELUPDATEUSERINFO;
	}
	
}
