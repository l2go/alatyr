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

/**
 * Pops up an icon on the left side of the screen and shows
 * a system message "The vitamin item has arrived".<BR>
 * After clicking the icon, a dialog "Your vitamin item has
 * arrived! Visit the vitamin manager in any village [...]
 * @author savormix
 */
public final class ExNotifyPremiumItem extends StaticPacket
{
	private static final String _S__EXNOTIFYPREMIUMITEM = "[S] FE:85 ExNotifyPremiumItem ch";
	public static final ExNotifyPremiumItem PACKET = new ExNotifyPremiumItem();
	
	private ExNotifyPremiumItem()
	{
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x85);
	}
	
	@Override
	public String getType()
	{
		return _S__EXNOTIFYPREMIUMITEM;
	}
}
