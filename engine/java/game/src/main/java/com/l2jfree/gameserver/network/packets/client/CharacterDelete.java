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
import com.l2jfree.gameserver.network.packets.server.CharacterDeleteFail;
import com.l2jfree.gameserver.network.packets.server.CharacterDeleteSuccess;

/**
 * This class represents a packet sent by the client when a character is being marked for deletion ("Yes" is clicked in the deletion confirmation dialog)
 */
public class CharacterDelete extends L2ClientPacket
{
	private static final String _C__CHARACTERDELETE = "[C] 0D CharacterDelete c[d]";
	
	private int _charSlot;
	
	@Override
	protected void readImpl()
	{
		_charSlot = readD();
	}
	
	@Override
	protected void runImpl()
	{
		if (_log.isDebugEnabled())
			_log.debug("deleting slot:" + _charSlot);
		
		try
		{
			byte answer = getClient().markToDeleteChar(_charSlot);
			
			switch (answer)
			{
				default:
				case -1: // Error
					break;
				case 0: // Success!
					sendPacket(CharacterDeleteSuccess.PACKET);
					break;
				case 1:
					sendPacket(new CharacterDeleteFail(CharacterDeleteFail.REASON_YOU_MAY_NOT_DELETE_CLAN_MEMBER));
					break;
				case 2:
					sendPacket(new CharacterDeleteFail(CharacterDeleteFail.REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED));
					break;
			}
		}
		catch (Exception e)
		{
			_log.error("Couldn't mark character for deletion!", e);
		}
		
		sendPacket(new CharSelectionInfo(getClient()));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__CHARACTERDELETE;
	}
}
