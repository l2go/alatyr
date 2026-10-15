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

import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 *
 * @author  KenM
 */
public class ExPartyPetWindowUpdate extends L2ServerPacket
{
	private final static String S_FE_19_EXPARTYPETWINDOWUPDATE = "[S] FE:19 ExPartyPetWindowUpdate";
	private final L2Summon _summon;
	
	public ExPartyPetWindowUpdate(L2Summon summon)
	{
		_summon = summon;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_FE_19_EXPARTYPETWINDOWUPDATE;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x19);
		writeD(_summon.getObjectId());
		writeD(_summon.getTemplate().getIdTemplate() + 1000000);
		writeD(_summon.getSummonType());
		writeD(_summon.getOwner().getObjectId());
		writeS(_summon.getName());
		writeD((int)_summon.getStatus().getCurrentHp());
		writeD(_summon.getMaxHp());
		writeD((int)_summon.getStatus().getCurrentMp());
		writeD(_summon.getMaxMp());
		writeD(_summon.getLevel());
	}
	
}
