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

import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Fort;

public class ExShowFortressInfo extends StaticPacket
{
	private static final String _S__EXSHOWFORTRESSINFO = "[S] FE:15 ExShowFortressInfo ch[d (dsdd)]";
	public static final ExShowFortressInfo PACKET = new ExShowFortressInfo();
	
	private ExShowFortressInfo()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x15);
		List<Fort> forts = FortManager.getInstance().getForts();
		writeD(forts.size());
		for (Fort fort : forts)
		{
			writeD(fort.getFortId());
			L2Clan clan = fort.getOwnerClan();
			if (clan != null)
				writeS(clan.getName());
			else
				writeS("");
			writeD(fort.getSiege().getIsInProgress());
			writeD(fort.getOwnedTime());
		}
	}
	
	@Override
	public String getType()
	{
		return _S__EXSHOWFORTRESSINFO;
	}
}
