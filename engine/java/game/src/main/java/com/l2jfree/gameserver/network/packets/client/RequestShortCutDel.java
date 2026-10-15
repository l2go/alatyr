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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * This class ...
 * 
 * @version $Revision: 1.3.4.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestShortCutDel extends L2ClientPacket
{
	private static final String _C__35_REQUESTSHORTCUTDEL = "[C] 35 RequestShortCutDel";
	
	private int _slot;
	private int _page;
	
	/**
	 * packet type id 0x35
	 * format:		cd
	 * @param rawPacket
	 */
	@Override
	protected void readImpl()
	{
		int id = readD();
		_slot = id % 12;
		_page = id / 12;
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (_page > 10 || _page < 0)
		{
			sendAF();
			return;
		}
		
		activeChar.deleteShortCut(_slot, _page);
		// client needs no confirmation. this packet is just to inform the server
		//sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__35_REQUESTSHORTCUTDEL;
	}
}
