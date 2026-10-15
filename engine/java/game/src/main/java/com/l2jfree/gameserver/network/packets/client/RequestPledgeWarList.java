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
import com.l2jfree.gameserver.network.packets.server.PledgeReceiveWarList;

/**
 * Format: (ch) dd
 * @author  -Wooden-
 */
public class RequestPledgeWarList extends L2ClientPacket
{
	private static final String _C__D0_1E_REQUESTPLEDGEWARLIST = "[C] D0:1E RequestPledgeWarList";
	
	//private int _unk1;
	private int _tab;
	
	@Override
	protected void readImpl()
	{
		/*_unk1 = */readD();
		_tab = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getClan() != null)
			sendPacket(new PledgeReceiveWarList(activeChar.getClan(), _tab));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_1E_REQUESTPLEDGEWARLIST;
	}
}
