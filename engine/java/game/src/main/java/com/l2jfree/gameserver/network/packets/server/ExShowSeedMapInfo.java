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

import com.l2jfree.gameserver.model.Location;

/**
 * Probably the destination coordinates where you should fly your clan's airship.<BR>
 * Exactly at the specified coordinates an airship controller is spawned.<BR>
 * Sent while being in Gracia, when world map is opened, in response to RequestSeedPhase.<BR>
 * FE A1 00		- opcodes<BR>
 * 02 00 00 00	- list size<BR>
 * <BR>
 * B7 3B FC FF	- x<BR>
 * 38 D8 03 00	- y<BR>
 * EB 10 00 00	- z<BR>
 * D3 0A 00 00	- heading?<BR>
 * <BR>
 * F6 BC FC FF	- x<BR>
 * 48 37 03 00	- y<BR>
 * 30 11 00 00	- z<BR>
 * CE 0A 00 00	- heading?
 * @author savormix
 */
public class ExShowSeedMapInfo extends StaticPacket
{
	private static final String _S__EXSHOWSEEDMAPINFO = "[S] FE:A1 ExShowSeedMapInfo";
	private static final Location[] ENTRANCES = { new Location(-246857, 251960, 4331, 2771), // Seed of Destruction
			new Location(-213770, 210760, 4400, 2766), // Seed of Immortality
	};
	public static final ExShowSeedMapInfo PACKET = new ExShowSeedMapInfo();
	
	private ExShowSeedMapInfo()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0xA1);
		
		writeD(ENTRANCES.length);
		for (Location loc : ENTRANCES)
		{
			writeD(loc.getX());
			writeD(loc.getY());
			writeD(loc.getZ());
			writeD(loc.getHeading());
		}
	}
	
	@Override
	public String getType()
	{
		return _S__EXSHOWSEEDMAPINFO;
	}
}
