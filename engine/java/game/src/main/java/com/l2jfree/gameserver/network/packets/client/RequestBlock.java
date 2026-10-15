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

public final class RequestBlock extends L2ClientPacket
{
	private static final String _C__A0_REQUESTBLOCK = "[C] A0 RequestBlock";
	
	private final static int BLOCK = 0;
	private final static int UNBLOCK = 1;
	private final static int BLOCKLIST = 2;
	private final static int ALLBLOCK = 3;
	private final static int ALLUNBLOCK = 4;
	
	private int _type;
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_type = readD();
		
		if (_type == BLOCK || _type == UNBLOCK)
			_name = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		switch (_type)
		{
			case BLOCK:
			{
				activeChar.getBlockList().add(_name);
				break;
			}
			case UNBLOCK:
			{
				activeChar.getBlockList().remove(_name);
				break;
			}
			case BLOCKLIST:
			{
				activeChar.getBlockList().sendListToOwner();
				break;
			}
			case ALLBLOCK:
			{
				activeChar.getBlockList().setBlockingAll(true);
				break;
			}
			case ALLUNBLOCK:
			{
				activeChar.getBlockList().setBlockingAll(false);
				break;
			}
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__A0_REQUESTBLOCK;
	}
}
