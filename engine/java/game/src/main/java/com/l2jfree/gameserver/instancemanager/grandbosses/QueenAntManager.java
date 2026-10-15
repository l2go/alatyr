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
package com.l2jfree.gameserver.instancemanager.grandbosses;

import java.util.Set;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.instance.QueenAntInstance;
import com.l2jfree.gameserver.gameobjects.instance.QueenAntLarvaInstance;
import com.l2jfree.gameserver.gameobjects.instance.QueenAntNurseInstance;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.world.spawn.SpawnListener;
import com.l2jfree.util.L2FastSet;

/**
 * @author hex1r0
 */
public final class QueenAntManager
{
	private static final L2CharPosition QUEEN_ANT_POS = new L2CharPosition(-21610, 181594, -5734, 0);
	private static final L2CharPosition QUEEN_ANT_LARVA_POS = new L2CharPosition(-21600, 179482, -5846, 0);
	
	private final Set<QueenAntNurseInstance> _nurses = new L2FastSet<QueenAntNurseInstance>().setShared(true);
	private QueenAntLarvaInstance _larva;
	private QueenAntInstance _queenAnt;
	
	private QueenAntManager()
	{
		L2Spawn.addSpawnListener(new SpawnListener() {
			@Override
			public void npcSpawned(L2Npc npc)
			{
				if (npc.getNpcId() == 29001)
					init((QueenAntInstance)npc);
			}
		});
	}
	
	private void init(QueenAntInstance queen)
	{
		_queenAnt = queen;
		
		for (int i = 0; i < 4; i++)
		{
			final L2Spawn nurseSpawn = new L2Spawn(29003);
			nurseSpawn.setAmount(1);
			nurseSpawn.setLocx(QUEEN_ANT_POS.x + (int)(400 * Math.cos(i * 1.407)));
			nurseSpawn.setLocy(QUEEN_ANT_POS.y + (int)(400 * Math.sin(i * 1.407)));
			nurseSpawn.setLocz(QUEEN_ANT_POS.z);
			nurseSpawn.setHeading(QUEEN_ANT_POS.heading);
			nurseSpawn.setRespawnDelay(Config.NURSEANT_RESPAWN_DELAY);
			nurseSpawn.startRespawn();
			
			_nurses.add((QueenAntNurseInstance)nurseSpawn.doSpawn());
		}
		
		final L2Spawn larvaSpawn = new L2Spawn(29002);
		larvaSpawn.setAmount(1);
		larvaSpawn.setLoc(QUEEN_ANT_LARVA_POS);
		larvaSpawn.stopRespawn();
		
		_larva = (QueenAntLarvaInstance)larvaSpawn.doSpawn();
	}
	
	public void queenAntDied()
	{
		_queenAnt = null;
		
		for (L2Npc n : _nurses)
		{
			n.getSpawn().stopRespawn();
			n.deleteMe();
		}
		_nurses.clear();
		
		_larva.getSpawn().stopRespawn();
		_larva.deleteMe();
		_larva = null;
	}
	
	public QueenAntInstance getQueenAntInstance()
	{
		return _queenAnt;
	}
	
	public QueenAntLarvaInstance getLarvaInstance()
	{
		return _larva;
	}
	
	private static final class SingletonHolder
	{
		public static final QueenAntManager INSTANCE = new QueenAntManager();
	}
	
	public static QueenAntManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
}
