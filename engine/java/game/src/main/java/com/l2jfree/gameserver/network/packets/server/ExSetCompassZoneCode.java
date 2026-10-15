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

public class ExSetCompassZoneCode extends StaticPacket
{
	private static final String _S__EXSETCOMPASSZONECODE = "[S] FE:32 ExSetCompassZoneCode ch[d]";
	public static final ExSetCompassZoneCode ALTERED_1 = new ExSetCompassZoneCode(0x08);
	public static final ExSetCompassZoneCode ALTERED_2 = new ExSetCompassZoneCode(0x09);
	public static final ExSetCompassZoneCode SHOW_PREVIOUS = new ExSetCompassZoneCode(0x0A);
	public static final ExSetCompassZoneCode SIEGE_WAR = new ExSetCompassZoneCode(0x0B);
	public static final ExSetCompassZoneCode PEACEFUL = new ExSetCompassZoneCode(0x0C);
	public static final ExSetCompassZoneCode SEVEN_SIGNS = new ExSetCompassZoneCode(0x0D);
	public static final ExSetCompassZoneCode PVP = new ExSetCompassZoneCode(0x0E);
	public static final ExSetCompassZoneCode GENERAL = new ExSetCompassZoneCode(0x0F);
	
	private final int _code;
	
	private ExSetCompassZoneCode(int code)
	{
		_code = code;
	}
	
	public final int getZoneCode()
	{
		return _code;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x33);
		
		writeD(_code);
	}
	
	@Override
	public String getType()
	{
		return _S__EXSETCOMPASSZONECODE;
	}
}
