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
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.StopRotation;
import com.l2jfree.gameserver.util.Broadcast;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.4.3 $ $Date: 2005/03/27 15:29:30 $
 */
public class FinishRotating extends L2ClientPacket
{
	private static final String _C__4B_FINISHROTATING = "[C] 4B FinishRotating";
	
	private int _degree;
	
	//	private int _unknown;
	
	/**
	 * packet type id 0x4a
	 * 
	 * sample
	 * 
	 * 4b
	 * d // degree
	 * d // unknown
	 * 
	 * format:		cdd
	 */
	@Override
	protected void readImpl()
	{
		_degree = readD();
		/*_unknown =*/readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		StopRotation sr = new StopRotation(player.getObjectId(), _degree, 0);
		Broadcast.toSelfAndKnownPlayers(player, sr);
		sr = null;
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__4B_FINISHROTATING;
	}
}
