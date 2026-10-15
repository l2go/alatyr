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

public class EnchantResult extends L2ServerPacket
{
	private static final String _S__87_ENCHANTRESULT = "[S] 87 EnchantResult";
	
	private final int _result;
	private final int _crystal;
	private final long _count;
	
	public EnchantResult(int result, int crystal, long count)
	{
		_result = result;
		_crystal = crystal;
		_count = count;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x87);
		writeD(_result);
		writeD(_crystal);
		writeCompQ(_count);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__87_ENCHANTRESULT;
	}
}
