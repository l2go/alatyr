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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2FriendlyMobInstance;

public class FriendlyMobKnownList extends AttackableKnownList
{
	// =========================================================
	// Data Field
	
	// =========================================================
	// Constructor
	public FriendlyMobKnownList(L2FriendlyMobInstance activeChar)
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
		
		if (object instanceof L2Player && getActiveChar().getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE)
			getActiveChar().getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
		
		return true;
	}
	
	@Override
	public boolean removeKnownObject(L2Object object)
	{
		if (!super.removeKnownObject(object))
			return false;
		
		if (!(object instanceof L2Creature))
			return true;
		
		if (getActiveChar().hasAI())
		{
			L2Creature temp = (L2Creature)object;
			getActiveChar().getAI().notifyEvent(CtrlEvent.EVT_FORGET_OBJECT, object);
			if (getActiveChar().getTarget() == temp)
				getActiveChar().setTarget(null);
		}
		
		if (getActiveChar().isVisible() && getKnownPlayers().isEmpty())
		{
			getActiveChar().clearAggroList();
			//removeAllKnownObjects();
			if (getActiveChar().hasAI())
				getActiveChar().getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE, null);
		}
		
		return true;
	}
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	@Override
	public final L2FriendlyMobInstance getActiveChar()
	{
		return (L2FriendlyMobInstance)_activeChar;
	}
}
