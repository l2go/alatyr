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

public class TradeDone extends StaticPacket
{
	private static final String _S__TRADEDONE = "[S] 1C TradeDone c[d]";
	public static final TradeDone COMPLETED = new TradeDone(true);
	public static final TradeDone CANCELLED = new TradeDone(false);
	
	private final boolean _completed;
	
	private TradeDone(boolean completed)
	{
		_completed = completed;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x1c);
		writeD(_completed);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__TRADEDONE;
	}
}
