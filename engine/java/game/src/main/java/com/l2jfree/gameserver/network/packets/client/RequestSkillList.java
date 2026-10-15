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
public class RequestSkillList extends L2ClientPacket
{
	private static final String _C__3F_REQUESTSKILLLIST = "[C] 3F RequestSkillList";
	
	/**
	 * packet type id 0x3f
	 * format:		c
	 * @param rawPacket
	 */
	@Override
	protected void readImpl()
	{
		// this is just a trigger packet. it has no content
	}
	
	@Override
	protected void runImpl()
	{
		L2Player cha = getClient().getActiveChar();
		if (cha == null)
			return;
		
		cha.sendSkillList();
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__3F_REQUESTSKILLLIST;
	}
}
