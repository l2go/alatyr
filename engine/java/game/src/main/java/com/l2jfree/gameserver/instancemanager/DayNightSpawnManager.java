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
package com.l2jfree.gameserver.instancemanager;


import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.instance.L2GrandBossInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2RaidBossInstance;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;

/**
 * This class ...
 * 
 * @version $Revision: $ $Date: $
 * @author godson
 */

public class DayNightSpawnManager
{
	private final static Logger _log = LoggerFactory.getLogger(DayNightSpawnManager.class);
	
	private static final class SingletonHolder
	{
		private static final DayNightSpawnManager INSTANCE = new DayNightSpawnManager();
	}
	
	public static DayNightSpawnManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final Map<L2Spawn, L2Npc> _dayCreatures;
	private final Map<L2Spawn, L2Npc> _nightCreatures;
	private final Map<L2Spawn, L2Boss> _bosses;
	
	private DayNightSpawnManager()
	{
		_dayCreatures = new LinkedHashMap<L2Spawn, L2Npc>();
		_nightCreatures = new LinkedHashMap<L2Spawn, L2Npc>();
		_bosses = new LinkedHashMap<L2Spawn, L2Boss>();
		
		_log.info("DayNightSpawnManager: Day/Night handler initialized");
	}
	
	public void addDayCreature(L2Spawn spawnDat)
	{
		if (_dayCreatures.containsKey(spawnDat))
		{
			_log.warn("DayNightSpawnManager: Spawn already added into day map");
			return;
		}
		_dayCreatures.put(spawnDat, null);
	}
	
	public void addNightCreature(L2Spawn spawnDat)
	{
		if (_nightCreatures.containsKey(spawnDat))
		{
			_log.warn("DayNightSpawnManager: Spawn already added into night map");
			return;
		}
		_nightCreatures.put(spawnDat, null);
	}
	
	/*
	 * Spawn Day Creatures, and Unspawn Night Creatures
	 */
	public void spawnDayCreatures()
	{
		spawnCreatures(_nightCreatures, _dayCreatures, "night", "day");
	}
	
	/*
	 * Spawn Night Creatures, and Unspawn Day Creatures
	 */
	public void spawnNightCreatures()
	{
		spawnCreatures(_dayCreatures, _nightCreatures, "day", "night");
	}
	
	/*
	 * Manage Spawn/Respawn
	 * Arg 1 : Map with L2Npc must be unspawned
	 * Arg 2 : Map with L2Npc must be spawned
	 * Arg 3 : String for log info for unspawned L2Npc
	 * Arg 4 : String for log info for spawned L2Npc
	 */
	private void spawnCreatures(Map<L2Spawn, L2Npc> UnSpawnCreatures, Map<L2Spawn, L2Npc> SpawnCreatures,
			String UnspawnLogInfo, String SpawnLogInfo)
	{
		try
		{
			if (!UnSpawnCreatures.isEmpty())
			{
				int i = 0;
				for (L2Npc dayCreature : UnSpawnCreatures.values())
				{
					if (dayCreature == null)
						continue;
					
					dayCreature.getSpawn().stopRespawn();
					dayCreature.deleteMe();
					i++;
				}
				if (_log.isDebugEnabled())
					_log.info("DayNightSpawnManager: Deleted " + i + " " + UnspawnLogInfo + " creatures");
			}
			
			int i = 0;
			L2Npc creature = null;
			for (L2Spawn spawnDat : SpawnCreatures.keySet())
			{
				if (SpawnCreatures.get(spawnDat) == null)
				{
					creature = spawnDat.doSpawn();
					if (creature == null)
						continue;
					
					SpawnCreatures.put(spawnDat, creature);
					creature.getStatus().setCurrentHp(creature.getMaxHp());
					creature.getStatus().setCurrentMp(creature.getMaxMp());
					creature.getSpawn().startRespawn();
					if (creature.isDecayed())
						creature.setDecayed(false);
					if (creature.isDead())
						creature.doRevive();
				}
				else
				{
					creature = SpawnCreatures.get(spawnDat);
					if (creature == null)
						continue;
					
					creature.getSpawn().startRespawn();
					if (creature.isDecayed())
						creature.setDecayed(false);
					if (creature.isDead())
						creature.doRevive();
					creature.getStatus().setCurrentHp(creature.getMaxHp());
					creature.getStatus().setCurrentMp(creature.getMaxMp());
					creature.spawnMe();
				}
				
				i++;
			}
			if (_log.isDebugEnabled())
				_log.info("DayNightSpawnManager: Spawning " + i + " " + SpawnLogInfo + " creatures");
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
	}
	
	private void changeMode(int mode)
	{
		if (_nightCreatures.size() == 0 && _dayCreatures.size() == 0)
			return;
		
		switch (mode)
		{
			case 0:
				spawnDayCreatures();
				specialNightBoss(0);
				break;
			case 1:
				spawnNightCreatures();
				specialNightBoss(1);
				break;
			default:
				_log.warn("DayNightSpawnManager: Wrong mode sent");
				break;
		}
	}
	
	public void notifyChangeMode()
	{
		try
		{
			if (GameTimeManager.getInstance().isNowNight())
				changeMode(1);
			else
				changeMode(0);
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
	}
	
	public void cleanUp()
	{
		_nightCreatures.clear();
		_dayCreatures.clear();
		_bosses.clear();
	}
	
	private void specialNightBoss(int mode)
	{
		try
		{
			for (L2Spawn spawn : _bosses.keySet())
			{
				L2Boss boss = _bosses.get(spawn);
				
				if (boss == null && mode == 1)
				{
					L2Npc npc = spawn.doSpawn();
					if (npc instanceof L2RaidBossInstance)
					{
						boss = (L2Boss)npc;
						RaidBossSpawnManager.getInstance().notifySpawnNightBoss(boss);
					}
					else if (npc instanceof L2GrandBossInstance)
					{
						boss = (L2Boss)npc;
						GrandBossSpawnManager.getInstance().notifySpawnNightBoss(boss);
					}
					else
						continue;
					
					_bosses.put(spawn, boss);
					continue;
				}
				
				if (boss == null)
					continue;
				
				if (boss.getNpcId() == 25328 && boss.getRaidStatus().equals(BossSpawnManager.StatusEnum.ALIVE))
					handleHellmans(boss, mode);
				
				if (mode == 0)
					continue;
				
				return;
			}
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
	}
	
	private void handleHellmans(L2Boss boss, int mode)
	{
		switch (mode)
		{
			case 0:
				boss.deleteMe();
				if (_log.isDebugEnabled())
					_log.info("DayNightSpawnManager: Deleting Hellman raidboss");
				break;
			case 1:
				boss.spawnMe();
				if (_log.isDebugEnabled())
					_log.info("DayNightSpawnManager: Spawning Hellman raidboss");
				break;
		}
	}
	
	public L2Boss handleBoss(L2Spawn spawnDat)
	{
		if (_bosses.containsKey(spawnDat))
			return _bosses.get(spawnDat);
		
		if (GameTimeManager.getInstance().isNowNight())
		{
			L2Boss raidboss = (L2Boss)spawnDat.doSpawn();
			_bosses.put(spawnDat, raidboss);
			return raidboss;
		}
		_bosses.put(spawnDat, null);
		return null;
	}
}
