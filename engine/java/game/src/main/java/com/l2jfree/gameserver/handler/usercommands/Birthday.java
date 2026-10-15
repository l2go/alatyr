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
package com.l2jfree.gameserver.handler.usercommands;

import java.util.Calendar;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IUserCommandHandler;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class Birthday implements IUserCommandHandler
{
	// 161714928 in Gracia P2
	private static final int[] COMMAND_IDS = { 126 };
	
	@Override
	public boolean useUserCommand(int id, L2Player activeChar)
	{
		Calendar bDay = activeChar.getCreationDate();
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_BIRTHDAY_IS_S3_S4_S2);
		sm.addPcName(activeChar);
		sm.addNumber(bDay.get(Calendar.DAY_OF_MONTH));
		sm.addNumber(bDay.get(Calendar.YEAR));
		sm.addNumber(bDay.get(Calendar.MONTH));
		activeChar.sendPacket(sm);
		if (activeChar.isBirthdayIllegal())
			activeChar.sendPacket(SystemMessageId.CHARACTERS_CREATED_FEB_29_WILL_RECEIVE_GIFT_FEB_28);
		return true;
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
