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

import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;

public final class DecayTaskManager extends AbstractPeriodicTaskManager
{
	public static final int RAID_BOSS_DECAY_TIME = 30000;
	public static final int ATTACKABLE_DECAY_TIME = 8500;
	
	public static DecayTaskManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<L2Creature, Long> _decayTasks = new ConcurrentHashMap<L2Creature, Long>();
	
	private DecayTaskManager()
	{
		super(1000);
	}
	
	public boolean hasDecayTask(L2Creature actor)
	{
		readLock();
		try
		{
			return _decayTasks.containsKey(actor);
		}
		finally
		{
			readUnlock();
		}
	}
	
	public double getRemainingDecayTime(L2Creature actor)
	{
		readLock();
		try
		{
			double remaining = _decayTasks.get(actor) - System.currentTimeMillis();
			
			return remaining / getDecayTime0(actor);
		}
		finally
		{
			readUnlock();
		}
	}
	
	public void addDecayTask(L2Creature actor)
	{
		writeLock();
		try
		{
			_decayTasks.put(actor, System.currentTimeMillis() + getDecayTime0(actor));
		}
		finally
		{
			writeUnlock();
		}
	}
	
	private int getDecayTime0(L2Creature actor)
	{
		if (actor instanceof L2MonsterInstance)
		{
			switch (((L2MonsterInstance)actor).getNpcId())
			{
				case 29019: // Antharas
				case 29066: // Antharas
				case 29067: // Antharas
				case 29068: // Antharas
					return 12000;
				case 29028: // Valakas
					return 18000;
				case 29014: // Orfen
				case 29001: // Queen Ant
					return 150000;
				case 29045: // Frintezza
					return 9500;
				case 29046: // Scarlet Van Halisha lvl 85 -> Morphing
					return 2000;
				case 29047: // Scarlet Van Halisha lvl 90
					return 7500;
			}
		}
		
		if (actor instanceof L2Boss)
			return RAID_BOSS_DECAY_TIME;
		
		if (actor instanceof L2PetInstance)
			return 86400000;
		
		return ATTACKABLE_DECAY_TIME;
	}
	
	public void cancelDecayTask(L2Creature actor)
	{
		writeLock();
		try
		{
			_decayTasks.remove(actor);
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
			for (Map.Entry<L2Creature, Long> entry : _decayTasks.entrySet())
			{
				if (System.currentTimeMillis() > entry.getValue())
				{
					final L2Creature actor = entry.getKey();
					
					actor.onDecay();
					
					_decayTasks.remove(actor);
				}
			}
		}
		finally
		{
			writeUnlock();
		}
	}
	
	public String getStats()
	{
		readLock();
		final StringBuilder sb = new StringBuilder();
		try
		{
			sb.append("============= DecayTask Manager Report ============").append("\r\n");
			sb.append("Tasks count: ").append(_decayTasks.size()).append("\r\n");
			sb.append("Tasks dump:").append("\r\n");
			
			for (L2Creature actor : _decayTasks.keySet())
			{
				sb.append("(").append(_decayTasks.get(actor) - System.currentTimeMillis()).append(") - ");
				sb.append(actor.getClass().getSimpleName()).append("/").append(actor.getName()).append("\r\n");
			}
			
			return sb.toString();
		}
		finally
		{
			readUnlock();
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final DecayTaskManager _instance = new DecayTaskManager();
	}
}
