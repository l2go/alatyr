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
package com.l2jfree.gameserver.gameobjects;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.BossSpawnManager;
import com.l2jfree.gameserver.instancemanager.RaidPointsManager;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.tools.random.Rnd;

public abstract class L2Boss extends L2MonsterInstance
{
	private static final int BOSS_MAINTENANCE_INTERVAL = 10000;
	public static final int BOSS_INTERACTION_DISTANCE = 500;
	public static final int BOSS_PENALTY_SILENCE = 4215;
	public static final int BOSS_PENALTY_PETRIFICATION = 4515;
	public static final int BOSS_PENALTY_RESISTANCE = 5479;
	
	public L2Boss(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	private BossSpawnManager.StatusEnum _raidStatus;
	
	@Override
	protected int getMaintenanceInterval()
	{
		return BOSS_MAINTENANCE_INTERVAL;
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		L2Player player = killer.getActingPlayer();
		if (player != null)
		{
			broadcastPacket(SystemMessageId.RAID_WAS_SUCCESSFUL.getSystemMessage());
			if (player.getParty() != null)
			{
				for (L2Player member : player.getParty().getPartyMembers())
					rewardRaidPoints(member);
			}
			else
				rewardRaidPoints(player);
		}
		return true;
	}
	
	private void rewardRaidPoints(L2Player player)
	{
		int points = (getLevel() / 2) + Rnd.get(-5, 5);
		RaidPointsManager.addPoints(player, getNpcId(), points);
		SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S1_RAID_POINTS);
		sm.addNumber(points);
		player.sendPacket(sm);
	}
	
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	@Override
	public boolean canInteract(L2Player player)
	{
		// TODO: NPC busy check etc...
		return isInsideRadius(player, BOSS_INTERACTION_DISTANCE, false, false);
	}
	
	public void setRaidStatus(BossSpawnManager.StatusEnum status)
	{
		_raidStatus = status;
	}
	
	public BossSpawnManager.StatusEnum getRaidStatus()
	{
		return _raidStatus;
	}
	
	/**
	 * Spawn all minions at a regular interval
	 * if minions are not near the raid boss, teleport them
	 * 
	 */
	@Override
	protected void startMaintenanceTask()
	{
		if (_minionList != null)
			_minionList.spawnMinions();
		
		_maintenanceTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				checkAndReturnToSpawn();
				
				if (_minionList != null)
					_minionList.maintainMinions();
			}
		}, 60000, getMaintenanceInterval() + Rnd.get(5000));
	}
	
	protected void checkAndReturnToSpawn()
	{
		if (isDead() || isMovementDisabled())
			return;
		
		switch (getNpcId())
		{
		// Gordon does not have permanent spawn
			case 29095:
				// Antharas lair is very big so just ignore
			case 29068:
			case 29067:
				// QA is handeled by AI script
			case 29001:
				// Valakas lair is very big so just ignore
			case 29028:
				// Zaken is handeled by AI script
			case 29022:
				// Orfen is handeled by AI script
			case 29014:
				return;
		}
		
		final L2Spawn spawn = getSpawn();
		if (spawn == null)
			return;
		
		final int spawnX = spawn.getLocx();
		final int spawnY = spawn.getLocy();
		final int spawnZ = spawn.getLocz();
		
		if (!isInCombat() && !isMovementDisabled())
		{
			if (!isInsideRadius(spawnX, spawnY, spawnZ, Math.max(Config.MAX_DRIFT_RANGE, 200), true, false))
				teleToLocation(spawnX, spawnY, spawnZ, false);
		}
	}
	
	/**
	 * Restore full Amount of HP and MP
	 * 
	 */
	public void healFull()
	{
		super.getStatus().setCurrentHp(super.getMaxHp());
		super.getStatus().setCurrentMp(super.getMaxMp());
	}
}
