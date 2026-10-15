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
package com.l2jfree.gameserver.gameobjects.knownlist;

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeGuardInstance;
import com.l2jfree.gameserver.model.entity.Castle;

public class SiegeGuardKnownList extends AttackableKnownList
{
	// =========================================================
	// Data Field
	
	// =========================================================
	// Constructor
	public SiegeGuardKnownList(L2SiegeGuardInstance activeChar)
	{
		super(activeChar);
	}
	
	// =========================================================
	// Method - Public
	@Override
	public boolean addKnownObject(L2Object object)
	{
		if (!super.addKnownObject(object))
			return false;
		
		Castle castle = getActiveChar().getCastle();
		// Check if siege is in progress
		if (castle != null && castle.getSiege().getIsInProgress())
		{
			L2Player player = null;
			if (object instanceof L2Player)
				player = (L2Player)object;
			else if (object instanceof L2Summon)
				player = ((L2Summon)object).getOwner();
			
			// Check if player is not the defender
			if (player != null
					&& (player.getClan() == null || castle.getSiege().getAttackerClan(player.getClan()) != null))
			{
				if (getActiveChar().getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE)
					getActiveChar().getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);//(L2Creature)object);
			}
		}
		return true;
	}
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	@Override
	public final L2SiegeGuardInstance getActiveChar()
	{
		return (L2SiegeGuardInstance)_activeChar;
	}
}
