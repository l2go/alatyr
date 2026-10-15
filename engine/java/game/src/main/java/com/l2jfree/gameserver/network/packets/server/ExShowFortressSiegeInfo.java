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


import java.util.List;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager.SiegeSpawn;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 *
 * @author  KenM
 */
public class ExShowFortressSiegeInfo extends L2ServerPacket
{
	private final static String S_FE_17_EXSHOWFORTRESSSIEGEINFO = "[S] FE:17 ExShowFortressSiegeInfo";
	
	private final int _fortId;
	private final int _size;
	private final Fort _fort;
	
	/**
	 * @param fortId
	 */
	public ExShowFortressSiegeInfo(Fort fort)
	{
		_fort = fort;
		_fortId = fort.getFortId();
		_size = fort.getFortSize();
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return S_FE_17_EXSHOWFORTRESSSIEGEINFO;
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x17);
		
		writeD(_fortId); // Fortress Id
		writeD(_size); // Total Barracks Count
		List<SiegeSpawn> commanders = FortSiegeManager.getInstance().getCommanderSpawnList(_fortId);
		if (commanders != null && !commanders.isEmpty())
		{
			switch (commanders.size())
			{
				case 3:
					switch (_fort.getSiege().getCommanders().get(_fortId).size())
					{
						case 0:
							writeD(0x03);
							break;
						case 1:
							writeD(0x02);
							break;
						case 2:
							writeD(0x01);
							break;
						case 3:
							writeD(0x00);
							break;
					}
					break;
				case 4: // TODO: change 4 to 5 once control room supported
					switch (_fort.getSiege().getCommanders().get(_fortId).size())
					// TODO: once control room supported, update writeD(0x0x) to support 5th room
					{
						case 0:
							writeD(0x05);
							break;
						case 1:
							writeD(0x04);
							break;
						case 2:
							writeD(0x03);
							break;
						case 3:
							writeD(0x02);
							break;
						case 4:
							writeD(0x01);
							break;
					}
					break;
			}
		}
		else
		{
			for (int i = 0; i < _size; i++)
				writeD(0x00);
		}
	}
}
