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
package com.l2jfree.gameserver.taskmanager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.network.packets.server.AutoAttackStop;

public final class AttackStanceTaskManager extends AbstractPeriodicTaskManager
{
	public static final long COMBAT_TIME = 15000;
	
	public static AttackStanceTaskManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<L2Creature, Long> _attackStanceTasks = new ConcurrentHashMap<L2Creature, Long>();
	
	private AttackStanceTaskManager()
	{
		super(1000);
	}
	
	public boolean getAttackStanceTask(L2Creature actor)
	{
		readLock();
		try
		{
			if (actor instanceof L2Summon)
				actor = ((L2Summon)actor).getOwner();
			
			return _attackStanceTasks.containsKey(actor);
		}
		finally
		{
			readUnlock();
		}
	}
	
	public void addAttackStanceTask(L2Creature actor)
	{
		writeLock();
		try
		{
			if (actor instanceof L2Summon)
				actor = ((L2Summon)actor).getOwner();
			
			if (actor instanceof L2Player)
				for (L2CubicInstance cubic : ((L2Player)actor).getCubics().values())
					if (cubic.getId() != L2CubicInstance.LIFE_CUBIC)
						cubic.doAction();
			
			_attackStanceTasks.put(actor, System.currentTimeMillis() + COMBAT_TIME);
		}
		finally
		{
			writeUnlock();
		}
	}
	
	public void removeAttackStanceTask(L2Creature actor)
	{
		writeLock();
		try
		{
			if (actor instanceof L2Summon)
				actor = ((L2Summon)actor).getOwner();
			
			_attackStanceTasks.remove(actor);
		}
		finally
		{
			writeUnlock();
		}
	}
	
	@Override
	public void run()
	{
		writeLock();
		try
		{
			for (Map.Entry<L2Creature, Long> entry : _attackStanceTasks.entrySet())
			{
				if (System.currentTimeMillis() > entry.getValue())
				{
					final L2Creature actor = entry.getKey();
					
					actor.broadcastPacket(new AutoAttackStop(actor.getObjectId()));
					
					if (actor instanceof L2Player)
					{
						final L2Summon pet = ((L2Player)actor).getPet();
						if (pet != null)
							pet.broadcastPacket(new AutoAttackStop(pet.getObjectId()));
					}
					
					actor.getAI().setAutoAttacking(false);
					
					_attackStanceTasks.remove(actor);
				}
			}
		}
		finally
		{
			writeUnlock();
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AttackStanceTaskManager _instance = new AttackStanceTaskManager();
	}
}
