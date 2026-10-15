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

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Trap;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.TrapKnownList;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;

public final class L2TrapInstance extends L2Trap implements Runnable
{
	private static final class TrapTaskManager extends AbstractIterativePeriodicTaskManager<L2TrapInstance>
	{
		private static final TrapTaskManager _instance = new TrapTaskManager();
		
		private static TrapTaskManager getInstance()
		{
			return _instance;
		}
		
		private TrapTaskManager()
		{
			super(1000);
		}
		
		@Override
		protected void callTask(L2TrapInstance task)
		{
			task.run();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "run()";
		}
	}
	
	private final L2Skill _skill;
	
	private int _totalLifeTime;
	private int _timeRemaining;
	private boolean _isDetected;
	
	public L2TrapInstance(int objectId, L2CreatureTemplate template, L2Player owner, int lifeTime, L2Skill skill)
	{
		super(objectId, template, owner);
		
		_skill = skill;
		
		_totalLifeTime = lifeTime == 0 ? 30000 : lifeTime;
		_timeRemaining = _totalLifeTime;
		
		TrapTaskManager.getInstance().startTask(this);
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		_totalLifeTime = 0;
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new TrapKnownList(this);
	}
	
	@Override
	public TrapKnownList getKnownList()
	{
		return (TrapKnownList)_knownList;
	}
	
	@Override
	public void run()
	{
		_timeRemaining -= 1000;
		
		if (_timeRemaining < _totalLifeTime - 15000)
			broadcastPacket(new SocialAction(getObjectId(), 2));
		
		if (_timeRemaining < 0)
		{
			L2Creature trg;
			
			switch (_skill.getTargetType())
			{
				case TARGET_AURA:
				case TARGET_FRONT_AURA:
				case TARGET_BEHIND_AURA:
					trg = L2TrapInstance.this;
					break;
				default:
					trg = getRandomTarget();
					break;
			}
			
			if (trg != null)
			{
				setTarget(trg);
				doCast(_skill);
				
				ThreadPoolManager.getInstance().schedule(new Runnable() {
					@Override
					public void run()
					{
						unSummon(getOwner());
						
						final L2Creature[] targetList = _skill.getTargetList(L2TrapInstance.this);
						
						if (targetList != null)
							for (L2Creature attacked : targetList)
								if (attacked instanceof L2Attackable)
									((L2Attackable)attacked).addDamage(getOwner(), 1);
					}
				}, _skill.getHitTime() + 2000);
			}
			else
			{
				unSummon(getOwner());
			}
		}
	}
	
	private L2Creature getRandomTarget()
	{
		for (L2Creature trg : getKnownList().getKnownCharactersInRadius(_skill.getSkillRadius()))
		{
			if (trg == getOwner() || trg.isInsideZone(L2Zone.FLAG_PEACE))
				continue;
			
			if (trg.getParty() != null && trg.getParty() == getOwner().getParty())
				continue;
			
			return trg;
		}
		
		return null;
	}
	
	@Override
	public void unSummon(L2Player owner)
	{
		TrapTaskManager.getInstance().stopTask(this);
		
		super.unSummon(owner);
	}
	
	@Override
	public void setDetected()
	{
		_isDetected = true;
	}
	
	@Override
	public boolean isDetected()
	{
		return _isDetected;
	}
}
