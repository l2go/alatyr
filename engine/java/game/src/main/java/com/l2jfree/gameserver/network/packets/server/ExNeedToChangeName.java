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

/**
 * A very strange packet.<BR>
 * If it's sent as a trigger, the player is informed that after
 * server integration, it's clan name (which is never filled in)
 * has overlapped and he must change it.<BR>
 * If we write a zero byte after opcodes, client ignores the packet.<BR>
 * If we write <U>anything</U> else after opcodes, the message changes
 * to something like "requested name invalid/unavailable, please try again"
 * <BR><BR>
 * The name is sent with RequestExChangeName.
 * @author savormix
 */
public final class ExNeedToChangeName extends L2ServerPacket
{
	private static final String _S__FE_69_EXNEEDTOCHANGENAME = "[S] FE:69 ExNeedToChangeName";
	
	public ExNeedToChangeName()
	{
		// TODO Auto-generated constructor stub
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x69);
		
		// write 0x01 if player must retry, nothing otherwise.
		writeD(0x01);
	}
	
	@Override
	public String getType()
	{
		return _S__FE_69_EXNEEDTOCHANGENAME;
	}
}
