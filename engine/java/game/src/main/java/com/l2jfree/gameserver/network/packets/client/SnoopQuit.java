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
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class SnoopQuit extends L2ClientPacket
{
	private static final String _C__SNOOPQUIT = "[C] B4 Snoop_quit c[d]";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getActiveChar();
		if (player == null)
			return;
		L2Player target = L2World.getInstance().findPlayer(_objectId);
		if (target == null)
		{
			requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			return;
		}
		
		player.removeSnooped(target);
		target.removeSnooper(player);
		player.sendMessage("Surveillance of player " + target.getName() + " canceled.");
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__SNOOPQUIT;
	}
}
