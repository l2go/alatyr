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
package com.l2jfree.gameserver.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.instance.L2MinionInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.OrfenInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.L2MinionData;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.util.L2FastSet;

/**
 * @author luisantonioa
 */
public final class MinionList
{
	private final static Logger _log = LoggerFactory.getLogger(L2MonsterInstance.class);
	
	/** List containing the current spawned minions for this L2MonsterInstance */
	private final Set<L2MinionInstance> minionReferences = new L2FastSet<L2MinionInstance>().setShared(true);
	private final Map<L2MinionInstance, Long> _respawnTasks = new ConcurrentHashMap<L2MinionInstance, Long>();
	private final L2MonsterInstance master;
	
	public MinionList(L2MonsterInstance pMaster)
	{
		master = pMaster;
	}
	
	public int countSpawnedMinions()
	{
		return minionReferences.size();
	}
	
	private int countSpawnedMinionsById(int minionId)
	{
		int count = 0;
		for (L2MinionInstance minion : getSpawnedMinions())
		{
			if (minion != null && minion.getNpcId() == minionId)
			{
				count++;
			}
		}
		
		return count;
	}
	
	public boolean hasMinions()
	{
		return !getSpawnedMinions().isEmpty();
	}
	
	public Set<L2MinionInstance> getSpawnedMinions()
	{
		return minionReferences;
	}
	
	public void addSpawnedMinion(L2MinionInstance minion)
	{
		minionReferences.add(minion);
	}
	
	public int lazyCountSpawnedMinionsGroups()
	{
		Set<Integer> seenGroups = new LinkedHashSet<Integer>();
		for (L2MinionInstance minion : getSpawnedMinions())
		{
			if (minion != null)
				seenGroups.add(minion.getNpcId());
		}
		
		return seenGroups.size();
	}
	
	public void moveMinionToRespawnList(L2MinionInstance minion)
	{
		minionReferences.remove(minion);
		
		_respawnTasks.put(minion, System.currentTimeMillis());
	}
	
	public void clearRespawnList()
	{
		_respawnTasks.clear();
	}
	
	/**
	 * Manage respawning of minions for this RaidBoss.<BR><BR>
	 */
	public void maintainMinions()
	{
		if (master.isAlikeDead())
			return;
		
		long current = System.currentTimeMillis();
		
		for (Map.Entry<L2MinionInstance, Long> entry : _respawnTasks.entrySet())
		{
			if (current - entry.getValue() > Config.RAID_MINION_RESPAWN_TIMER)
			{
				spawnSingleMinion(entry.getKey().getNpcId(), master.getInstanceId());
				
				_respawnTasks.remove(entry.getKey());
			}
		}
	}
	
	/**
	 * Manage the spawn of all Minions of this RaidBoss.<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the Minion data of all Minions that must be spawn </li>
	 * <li>For each Minion type, spawn the amount of Minion needed </li><BR><BR>
	 * 
	 * @param player The L2Player to attack
	 * 
	 */
	public void spawnMinions()
	{
		if (master.isAlikeDead())
			return;
		
		L2MinionData[] minions = master.getTemplate().getMinionData();
		if (minions == null)
			return;
		
		int minionCount = 0, minionId = 0, minionsToSpawn;
		for (L2MinionData minion : minions)
		{
			switch (master.getNpcId())
			{
				case 29014: // Orfen
					OrfenInstance orfen = (OrfenInstance)master;
					switch (orfen.getPos())
					{
						case FIELD:
							minionCount = 4;
							minionId = 29016;
							break;
						case NEST:
							minionCount = minion.getAmount();
							minionId = minion.getMinionId();
							break;
					}
					break;
				default:
					minionCount = minion.getAmount();
					minionId = minion.getMinionId();
			}
			
			minionsToSpawn = minionCount - countSpawnedMinionsById(minionId);
			
			for (int i = 0; i < minionsToSpawn; i++)
			{
				spawnSingleMinion(minionId, master.getInstanceId());
			}
		}
	}
	
	/**
	 * Init a Minion and add it in the world as a visible object.<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the template of the Minion to spawn </li>
	 * <li>Create and Init the Minion and generate its Identifier </li>
	 * <li>Set the Minion HP, MP and Heading </li>
	 * <li>Set the Minion leader to this RaidBoss </li>
	 * <li>Init the position of the Minion and add it in the world as a visible object </li><BR><BR>
	 * 
	 * @param minionid The I2NpcTemplate Identifier of the Minion to spawn
	 * 
	 */
	private void spawnSingleMinion(int minionid, int instanceId)
	{
		// Get the template of the Minion to spawn
		L2NpcTemplate minionTemplate = NpcTable.getInstance().getTemplate(minionid);
		
		// Create and Init the Minion and generate its Identifier
		L2MinionInstance monster = new L2MinionInstance(IdFactory.getInstance().getNextId(), minionTemplate);
		
		if (Config.CHAMPION_MINIONS && master.isChampion())
			monster.setChampion(true);
		
		// Set the Minion HP, MP and Heading
		monster.getStatus().setCurrentHpMp(monster.getMaxHp(), monster.getMaxMp());
		monster.setHeading(master.getHeading());
		
		// Set the Minion leader to this RaidBoss
		monster.setLeader(master);
		
		//move monster to masters instance
		monster.setInstanceId(instanceId);
		
		// Init the position of the Minion and add it in the world as a visible object
		int spawnConstant;
		int randSpawnLim = 170;
		int randPlusMin = 1;
		spawnConstant = Rnd.nextInt(randSpawnLim);
		//randomize +/-
		randPlusMin = Rnd.nextInt(2);
		if (randPlusMin == 1)
			spawnConstant *= -1;
		int newX = master.getX() + Math.round(spawnConstant);
		spawnConstant = Rnd.nextInt(randSpawnLim);
		//randomize +/-
		randPlusMin = Rnd.nextInt(2);
		if (randPlusMin == 1)
			spawnConstant *= -1;
		int newY = master.getY() + Math.round(spawnConstant);
		
		monster.spawnMe(newX, newY, master.getZ());
		
		if (_log.isDebugEnabled())
			_log.debug("Spawned minion template " + minionTemplate.getNpcId() + " with objid: " + monster.getObjectId()
					+ " to boss " + master.getObjectId() + " ,at: " + monster.getX() + " x, " + monster.getY() + " y, "
					+ monster.getZ() + " z");
	}
}
