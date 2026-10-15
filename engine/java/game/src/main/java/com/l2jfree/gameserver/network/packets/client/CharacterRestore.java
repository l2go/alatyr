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

import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;

/**
 * This class represents a packet sent by the client when a marked to delete character is being restored ("Yes" is clicked in the restore confirmation dialog)
 */
public class CharacterRestore extends L2ClientPacket
{
	private static final String _C__CHARACTERRESTORE = "[C] 7B CharacterRestore c[d]";
	
	private int _charSlot;
	
	@Override
	protected void readImpl()
	{
		_charSlot = readD();
	}
	
	@Override
	protected void runImpl()
	{
		try
		{
			getClient().markRestoredChar(_charSlot);
		}
		catch (Exception e)
		{
			_log.warn("Couldn't mark character as restored!", e);
		}
		
		sendPacket(new CharSelectionInfo(getClient()));
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__CHARACTERRESTORE;
	}
}
