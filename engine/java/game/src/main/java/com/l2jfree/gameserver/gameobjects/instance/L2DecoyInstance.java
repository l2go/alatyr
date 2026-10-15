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

import java.util.concurrent.Future;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Decoy;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.DecoyKnownList;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillDecoy;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;

public class L2DecoyInstance extends L2Decoy
{
	private int _totalLifeTime;
	private int _timeRemaining;
	private Future<?> _DecoyLifeTask;
	private Future<?> _HateSpam;
	
	public L2DecoyInstance(int objectId, L2NpcTemplate template, L2Player owner, L2SkillDecoy skill)
	{
		super(objectId, template, owner);
		if (skill != null)
		{
			_totalLifeTime = skill.getTotalLifeTime();
		}
		else
		{
			_totalLifeTime = 20000;
		}
		int delay = 1000;
		int skilllevel = getTemplate().getIdTemplate() - 13070;
		_timeRemaining = _totalLifeTime;
		_DecoyLifeTask =
				ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new DecoyLifetime(getOwner(), this), delay,
						delay);
		_HateSpam =
				ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(
						new HateSpam(this, SkillTable.getInstance().getInfo(5272, skilllevel)), 2000, 5000);
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		if (_HateSpam != null)
		{
			_HateSpam.cancel(true);
			_HateSpam = null;
		}
		_totalLifeTime = 0;
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new DecoyKnownList(this);
	}
	
	@Override
	public DecoyKnownList getKnownList()
	{
		return (DecoyKnownList)_knownList;
	}
	
	static class DecoyLifetime implements Runnable
	{
		private final L2Player _activeChar;
		private final L2DecoyInstance _Decoy;
		
		DecoyLifetime(L2Player activeChar, L2DecoyInstance Decoy)
		{
			_activeChar = activeChar;
			_Decoy = Decoy;
		}
		
		@Override
		public void run()
		{
			double newTimeRemaining;
			_Decoy.decTimeRemaining(1000);
			newTimeRemaining = _Decoy.getTimeRemaining();
			if (newTimeRemaining < 0)
			{
				_Decoy.unSummon(_activeChar);
			}
		}
	}
	
	static class HateSpam implements Runnable
	{
		private final L2DecoyInstance _activeChar;
		private final L2Skill _skill;
		
		HateSpam(L2DecoyInstance activeChar, L2Skill Hate)
		{
			_activeChar = activeChar;
			_skill = Hate;
		}
		
		@Override
		public void run()
		{
			_activeChar.setTarget(_activeChar);
			_activeChar.doCast(_skill);
		}
	}
	
	@Override
	public void unSummon(L2Player owner)
	{
		if (_DecoyLifeTask != null)
		{
			_DecoyLifeTask.cancel(true);
			_DecoyLifeTask = null;
		}
		if (_HateSpam != null)
		{
			_HateSpam.cancel(true);
			_HateSpam = null;
		}
		super.unSummon(owner);
	}
	
	public void decTimeRemaining(int value)
	{
		_timeRemaining -= value;
	}
	
	public int getTimeRemaining()
	{
		return _timeRemaining;
	}
	
	public int getTotalLifeTime()
	{
		return _totalLifeTime;
	}
}
