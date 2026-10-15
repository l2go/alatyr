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
package com.l2jfree.gameserver.network.packets.server;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Player.TimeStamp;
import com.l2jfree.gameserver.network.L2Client;

/**
 * @author KenM
 */
public final class SkillCoolTime extends StaticPacket
{
	public static final SkillCoolTime STATIC_PACKET = new SkillCoolTime();
	
	private SkillCoolTime()
	{
	}
	
	@Override
	public String getType()
	{
		return "[S] C7 SkillCoolTime";
	}
	
	@Override
	protected void writeImpl(L2Client client, L2Player activeChar)
	{
		if (activeChar == null)
			return;
		
		writeC(0xc7);
		// the size and the entries come from one copy: the stamps change while the packet is written
		List<TimeStamp> stamps = new ArrayList<TimeStamp>(activeChar.getReuseTimeStamps().values());
		writeD(stamps.size()); // list size
		for (TimeStamp ts : stamps)
		{
			writeD(ts.getSkillId());
			writeD(0x00);
			writeD(Math.round(ts.getReuseDelay() / 1000.f));
			writeD(Math.round(ts.getRemaining() / 1000.f));
		}
	}
}
