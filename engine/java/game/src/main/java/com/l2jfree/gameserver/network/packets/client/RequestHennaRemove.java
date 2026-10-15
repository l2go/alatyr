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
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * packet type id 0xbb
 * format:		cd
 */
public final class RequestHennaRemove extends L2ClientPacket
{
	private static final String _C__BC_RequestHennaRemove = "[C] bc RequestHennaRemove";
	private int _symbolId;
	
	@Override
	protected void readImpl()
	{
		_symbolId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		for (int i = 1; i <= 3; i++)
		{
			L2Henna henna = activeChar.getHenna(i);
			if (henna != null && henna.getSymbolId() == _symbolId)
			{
				if (activeChar.getAdena() >= (henna.getPrice() / 5))
					activeChar.removeHenna(i);
				else
					sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			}
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__BC_RequestHennaRemove;
	}
}
