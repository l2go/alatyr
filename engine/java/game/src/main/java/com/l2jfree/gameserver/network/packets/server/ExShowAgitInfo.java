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

import java.util.Map;

import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.entity.ClanHall;

public class ExShowAgitInfo extends StaticPacket
{
	private static final String _S__EXSHOWAGITINFO = "[S] FE:16 ExShowAgitInfo ch[d (dssd)]";
	public static final ExShowAgitInfo PACKET = new ExShowAgitInfo();
	
	private ExShowAgitInfo()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x16);
		Map<Integer, ClanHall> clannhalls = ClanHallManager.getInstance().getAllClanHalls();
		writeD(clannhalls.size());
		for (ClanHall ch : clannhalls.values())
		{
			writeD(ch.getId());
			writeS(ch.getOwnerId() <= 0 ? "" : ClanTable.getInstance().getClan(ch.getOwnerId()).getName()); // owner clan name
			writeS(ch.getOwnerId() <= 0 ? "" : ClanTable.getInstance().getClan(ch.getOwnerId()).getLeaderName()); // leader name
			writeD(ch.getSiege() != null); // 0 - auction  1 - war clanhall  2 - ETC (rainbow spring clanhall)
		}
	}
	
	@Override
	public String getType()
	{
		return _S__EXSHOWAGITINFO;
	}
}
