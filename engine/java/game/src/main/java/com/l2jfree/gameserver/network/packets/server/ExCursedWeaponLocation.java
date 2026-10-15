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
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author -Wooden-
 */
public class ExCursedWeaponLocation extends L2ServerPacket
{
	private static final String _S__FE_47_EXCURSEDWEAPONLOCATION = "[S] FE:47 ExCursedWeaponLocation [d(dd ddd)]";
	private final List<CursedWeaponInfo> _cursedWeaponInfo;
	
	public ExCursedWeaponLocation(List<CursedWeaponInfo> cursedWeaponInfo)
	{
		_cursedWeaponInfo = cursedWeaponInfo;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x47);
		
		if (!_cursedWeaponInfo.isEmpty())
		{
			writeD(_cursedWeaponInfo.size());
			for (CursedWeaponInfo info : _cursedWeaponInfo)
			{
				writeD(info.id);
				writeD(info.activated);
				
				writeD(info.loc.getX());
				writeD(info.loc.getY());
				writeD(info.loc.getZ());
			}
		}
		else
		{
			writeD(0);
			writeD(0);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_47_EXCURSEDWEAPONLOCATION;
	}
	
	public static class CursedWeaponInfo
	{
		public final Location loc;
		public final int id;
		public final int activated; //0 - not activated ? 1 - activated
		
		public CursedWeaponInfo(Location pLoc, int ID, int status)
		{
			loc = pLoc;
			id = ID;
			activated = status;
		}
	}
}
