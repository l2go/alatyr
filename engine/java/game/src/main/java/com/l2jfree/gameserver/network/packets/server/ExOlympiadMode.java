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

public final class ExOlympiadMode extends StaticPacket
{
	private static final String _S__OLYMPIADMODE = "[S] FE:7C ExOlympiadMode ch[c]";
	
	public static final ExOlympiadMode SPECTATE = new ExOlympiadMode(0x03);
	public static final ExOlympiadMode INGAME = new ExOlympiadMode(0x02);
	public static final ExOlympiadMode RETURN = new ExOlympiadMode(0x00);
	
	private final int _mode;
	
	private ExOlympiadMode(int mode)
	{
		_mode = mode;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x7c);
		
		writeC(_mode);
	}
	
	@Override
	public String getType()
	{
		return _S__OLYMPIADMODE;
	}
}
