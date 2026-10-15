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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.RecommendationManager;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class VoteSociality extends L2ClientPacket
{
	private static final String _C__VOTESOCIALITY = "[C] C2 VoteSociality c[d]";
	
	private int _targetId;
	
	@Override
	protected void readImpl()
	{
		_targetId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Object target = null;
		// Get object from target
		if (activeChar.getTargetId() == _targetId)
			target = activeChar.getTarget();
		if (target == null)
			target = L2World.getInstance().findObject(_targetId);
		
		if (target instanceof L2Player)
			RecommendationManager.getInstance().recommend(activeChar, target.getActingPlayer());
		else
			sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__VOTESOCIALITY;
	}
}
