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
package com.l2jfree.gameserver.gameobjects.status;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public final class SummonStatus extends CreatureStatus
{
	public SummonStatus(L2SummonInstance activeChar)
	{
		super(activeChar);
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		super.reduceHp0(value, attacker, awake, isDOT, isConsume);
		
		if (getActiveChar().getOwner() != null)
		{
			SystemMessage smsg = new SystemMessage(SystemMessageId.C1_RECEIVED_DAMAGE_OF_S3_FROM_C2);
			smsg.addCharName(getActiveChar());
			smsg.addCharName(attacker);
			smsg.addNumber((int)value);
			getActiveChar().getOwner().sendPacket(smsg);
		}
	}
	
	@Override
	public L2SummonInstance getActiveChar()
	{
		return (L2SummonInstance)_activeChar;
	}
}
