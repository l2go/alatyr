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
package com.l2jfree.gameserver.gameobjects.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2SiegeGuard;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.threadmanager.FIFOExecutableQueue;

/**
 * @author mkizub
 */
public class L2DoorAI extends L2CreatureAI
{
	public L2DoorAI(L2DoorInstance.AIAccessor accessor)
	{
		super(accessor);
	}
	
	// rather stupid AI... well, it's for doors :D
	@Override
	protected void onIntentionIdle()
	{
	}
	
	@Override
	protected void onIntentionActive()
	{
	}
	
	@Override
	protected void onIntentionRest()
	{
	}
	
	@Override
	protected void onIntentionAttack(L2Creature target)
	{
	}
	
	@Override
	protected void onIntentionCast(SkillUsageRequest request)
	{
	}
	
	@Override
	protected void onIntentionMoveTo(L2CharPosition destination)
	{
	}
	
	@Override
	protected void onIntentionFollow(L2Creature target)
	{
	}
	
	@Override
	protected void onIntentionPickUp(L2Object item)
	{
	}
	
	@Override
	protected void onIntentionInteract(L2Object object)
	{
	}
	
	@Override
	protected void onEvtThink()
	{
	}
	
	private GuardNotificationQueue _guardNotificationTasks;
	
	@Override
	protected void onEvtAttacked(L2Creature attacker)
	{
		if (_guardNotificationTasks == null)
			_guardNotificationTasks = new GuardNotificationQueue();
		
		_guardNotificationTasks.add(attacker);
	}
	
	@Override
	protected void onEvtAggression(L2Creature target, int aggro)
	{
	}
	
	@Override
	protected void onEvtStunned(L2Creature attacker)
	{
	}
	
	@Override
	protected void onEvtSleeping(L2Creature attacker)
	{
	}
	
	@Override
	protected void onEvtRooted(L2Creature attacker)
	{
	}
	
	@Override
	protected void onEvtReadyToAct()
	{
	}
	
	@Override
	protected void onEvtUserCmd(Object arg0, Object arg1)
	{
	}
	
	@Override
	protected void onEvtArrived()
	{
	}
	
	@Override
	protected void onEvtArrivedRevalidate()
	{
	}
	
	@Override
	protected void onEvtArrivedBlocked(L2CharPosition blocked_at_pos)
	{
	}
	
	@Override
	protected void onEvtForgetObject(L2Object object)
	{
	}
	
	@Override
	protected void onEvtCancel()
	{
	}
	
	@Override
	protected void onEvtDead()
	{
	}
	
	private final boolean isGuarding(L2Object o)
	{
		if (o instanceof L2Attackable)
		{
			if (o instanceof L2SiegeGuard)
				return true;
			switch (((L2Attackable)o).getNpcId())
			{
				case 35411:
				case 35412:
				case 35413:
				case 35414:
				case 35415:
				case 35416:
				case 35369:
				case 35370:
				case 35371:
				case 35372:
				case 35373:
				case 35382:
				case 35383:
					return true;
			}
		}
		return false;
	}
	
	private final class GuardNotificationQueue extends FIFOExecutableQueue
	{
		private final LinkedHashMap<L2Creature, Integer> _map = new LinkedHashMap<L2Creature, Integer>();
		
		private void add(L2Creature attacker)
		{
			synchronized (_map)
			{
				_map.merge(attacker, 15, Integer::sum);
			}
			execute();
		}
		
		@Override
		protected boolean isEmpty()
		{
			synchronized (_map)
			{
				return _map.isEmpty();
			}
		}
		
		@Override
		protected void removeAndExecuteFirst()
		{
			L2Creature attacker = null;
			int aggro = 0;
			
			synchronized (_map)
			{
				Map.Entry<L2Creature, Integer> first = _map.pollFirstEntry();
				
				attacker = first.getKey();
				aggro = first.getValue();
			}
			
			getActor().getKnownList().updateKnownObjects();
			
			for (L2Object obj : getActor().getKnownList().getKnownObjects().values())
			{
				if (isGuarding(obj))
				{
					L2Attackable guard = (L2Attackable)obj;
					if (Math.abs(attacker.getZ() - guard.getZ()) < 200)
						if (getActor().isInsideRadius(guard, guard.getFactionRange(), false, true))
							guard.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, attacker, aggro);
				}
			}
		}
	}
}
