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

public class ExShowAdventurerGuideBook extends StaticPacket
{
	private static final String _S__EXSHOWADVENTURERGUIDEBOOK = "[S] FE:38 ExShowAdventurerGuideBook ch";
	public static final ExShowAdventurerGuideBook PACKET = new ExShowAdventurerGuideBook();
	
	private ExShowAdventurerGuideBook()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x38);
	}
	
	@Override
	public String getType()
	{
		return _S__EXSHOWADVENTURERGUIDEBOOK;
	}
}
