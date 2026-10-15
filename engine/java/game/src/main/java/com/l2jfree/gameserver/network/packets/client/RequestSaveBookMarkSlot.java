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

public final class RequestSaveBookMarkSlot extends L2ClientPacket
{
	private static final String _C__REQUESTSAVEBOOKMARKSLOT = "[C] D0:51:01 RequestSaveBookMarkSlot chd[sds]";
	
	private String _name, _tag;
	private int _icon;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
		_icon = readD();
		_tag = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		activeChar.teleportBookmarkAdd(activeChar.getX(), activeChar.getY(), activeChar.getZ(), _icon, _tag, _name);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTSAVEBOOKMARKSLOT;
	}
}
