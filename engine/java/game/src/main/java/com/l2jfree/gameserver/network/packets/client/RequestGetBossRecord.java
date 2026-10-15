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
import com.l2jfree.gameserver.instancemanager.RaidPointsManager;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ExGetBossRecord;

public class RequestGetBossRecord extends L2ClientPacket
{
	private static final String _C__REQUESTGETBOSSRECORD = "[C] D0:40 RequestGetBossRecord ch[d]";
	
	//private int				_unk;
	
	@Override
	protected void readImpl()
	{
		/*_unk = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		int points = RaidPointsManager.getPointsByOwnerId(activeChar.getObjectId());
		int ranking = RaidPointsManager.calculateRanking(activeChar.getObjectId());
		
		sendPacket(new ExGetBossRecord(ranking, points, RaidPointsManager.getList(activeChar)));
		
		// no AF here
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTGETBOSSRECORD;
	}
}
