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
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;

public class ExShowCastleInfo extends StaticPacket
{
	private static final String _S__EXSHOWCASTLEINFO = "[S] FE:14 ExShowCastleInfo ch[d (dsdd)]";
	public static final ExShowCastleInfo PACKET = new ExShowCastleInfo();
	
	private ExShowCastleInfo()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x14);
		Map<Integer, Castle> castles = CastleManager.getInstance().getCastles();
		writeD(castles.size());
		for (Castle castle : castles.values())
		{
			writeD(castle.getCastleId());
			if (castle.getOwnerId() > 0)
			{
				L2Clan owner = ClanTable.getInstance().getClan(castle.getOwnerId());
				if (owner != null)
					writeS(owner.getName());
				else
				{
					_log.warn("Castle owner with no clan! Castle: " + castle.getName() + " has an ownerId="
							+ castle.getOwnerId() + " which doesn't have a L2Clan!");
					writeS("");
				}
			}
			else
				writeS("");
			writeD(castle.getTaxPercent());
			writeD((int)(castle.getSiege().getSiegeDate().getTimeInMillis() / 1000));
		}
	}
	
	@Override
	public String getType()
	{
		return _S__EXSHOWCASTLEINFO;
	}
}
