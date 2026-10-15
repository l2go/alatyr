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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 *
 * @author  devScarlet & mrTJO
 */
public final class ServerObjectInfo extends L2ServerPacket
{
	private static final String _S__92_SERVEROBJECTINFO = "[S] 92 ServerObjectInfo";
	private final L2Npc _activeChar;
	private final int _x, _y, _z, _heading;
	private final int _idTemplate;
	private final double _collisionHeight, _collisionRadius;
	
	public ServerObjectInfo(L2Npc activeChar)
	{
		_activeChar = activeChar;
		_idTemplate = _activeChar.getTemplate().getIdTemplate();
		_collisionHeight = _activeChar.getCollisionHeight();
		_collisionRadius = _activeChar.getCollisionRadius();
		_x = _activeChar.getX();
		_y = _activeChar.getY();
		_z = _activeChar.getZ();
		_heading = _activeChar.getHeading();
	}
	
	/**
	 * @see L2ServerPacket.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected final void writeImpl(L2Client client, L2Player activeChar)
	{
		boolean isAttackable = _activeChar.isAutoAttackable(activeChar);
		
		writeC(0x92);
		writeD(_activeChar.getObjectId());
		writeD(_idTemplate + 1000000);
		writeS(""); // name
		writeD(isAttackable ? 1 : 0);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_heading);
		writeF(1.0); // movement multiplier
		writeF(1.0); // attack speed multiplier
		writeF(_collisionRadius);
		writeF(_collisionHeight);
		writeD((int)(isAttackable ? _activeChar.getCurrentHp() : 0));
		writeD(isAttackable ? _activeChar.getMaxHp() : 0);
		writeD(0x01); // object type
		writeD(0x00); // special effects
	}
	
	/**
	 * @see L2ServerPacket.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__92_SERVEROBJECTINFO;
	}
}
