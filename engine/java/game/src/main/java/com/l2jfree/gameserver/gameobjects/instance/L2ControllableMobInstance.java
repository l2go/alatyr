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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2ControllableMobAI;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.status.ControllableMobStatus;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;

/**
 * @author littlecrow
 */
public class L2ControllableMobInstance extends L2MonsterInstance
{
	private boolean _isInvul;
	
	protected class ControllableAIAcessor extends AIAccessor
	{
		/*@Override
		public void detachAI()
		{
			// Do nothing, AI of controllable mobs can't be detached automatically
		}*/
	}
	
	@Override
	public boolean isAggressive()
	{
		return true;
	}
	
	@Override
	public int getAggroRange()
	{
		// Force mobs to be aggro
		return 500;
	}
	
	public L2ControllableMobInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	protected boolean canReplaceAI()
	{
		return false;
	}
	
	@Override
	protected L2CreatureAI initAI()
	{
		return new L2ControllableMobAI(new ControllableAIAcessor());
	}
	
	@Override
	public boolean isInvul()
	{
		return super.isInvul() || _isInvul;
	}
	
	public void setInvul(boolean isInvul)
	{
		_isInvul = isInvul;
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new ControllableMobStatus(this);
	}
	
	@Override
	public ControllableMobStatus getStatus()
	{
		return (ControllableMobStatus)_status;
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		removeAI();
		return true;
	}
	
	@Override
	public void deleteMe()
	{
		removeAI();
		super.deleteMe();
	}
	
	/**
	 * Definitively remove AI
	 */
	protected void removeAI()
	{
		synchronized (this)
		{
			if (hasAI())
			{
				getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				getAI().stopAITask();
			}
		}
	}
}
