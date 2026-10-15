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
public class RequestTargetCanceld extends L2ClientPacket
{
	private static final String _C__37_REQUESTTARGETCANCELD = "[C] 37 RequestTargetCanceld";
	
	private int _unselect;
	
	/**
	 * packet type id 0x37
	 * packet format rev656  ch
	 * @param rawPacket
	 */
	@Override
	protected void readImpl()
	{
		_unselect = readH();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (!activeChar.canChangeLockedTarget(null))
		{
			sendAF();
			return;
		}
		
		if (_unselect == 0)
		{
			if (activeChar.isCastingNow())
				activeChar.abortCast();
			else if (activeChar.getTarget() != null)
				activeChar.setTarget(null);
		}
		else if (activeChar.getTarget() != null)
			activeChar.setTarget(null);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__37_REQUESTTARGETCANCELD;
	}
}
