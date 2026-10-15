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

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class RadarControl extends L2ServerPacket
{
	private static final String _S__RADARCONTROL = "[S] F1 RadarControl c[ddddd]";
	
	public static final int MARKER_ADD = 0;
	public static final int MARKER_REMOVE = 1;
	private static final int MARKER_REMOVE_ALL = 2;
	public static final RadarControl REMOVE_ALL = new RadarControl(MARKER_REMOVE_ALL, 0, 0, 0, 0);
	
	// Identical
	public static final int FLAG_1 = 1;
	public static final int FLAG_2 = 2;
	
	private final int _marker;
	private final int _flag;
	private final int _x;
	private final int _y;
	private final int _z;
	
	public RadarControl(int marker, int flag, int x, int y, int z)
	{
		_marker = marker;
		_flag = flag;
		_x = x;
		_y = y;
		_z = z;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xF1);
		writeD(_marker);
		writeD(_flag);
		writeD(_x);
		writeD(_y);
		writeD(_z);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__RADARCONTROL;
	}
}
