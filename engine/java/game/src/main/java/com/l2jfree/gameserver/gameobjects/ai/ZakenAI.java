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

import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature.AIAccessor;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.lang.L2Math;
import com.l2jfree.tools.random.Rnd;

/**
 * @author hex1r0
 */
public class ZakenAI extends L2AttackableAI
{
	private static final int[] MINION_IDS = { 29023, 29024, 29026, 29027 };
	private static final int[] CHANCES = { 90, 80, 50, 75 };
	
	private long _lastMinionSpawn = System.currentTimeMillis();
	
	public ZakenAI(AIAccessor accessor)
	{
		super(accessor);
	}
	
	@Override
	protected void thinkActive()
	{
		super.thinkActive();
		
		if (Rnd.nextInt(1000) == 0)
		{
			_actor.doCast(SkillTable.getInstance().getInfo(4222, 1));
		}
	}
	
	@Override
	protected void thinkAttack()
	{
		super.thinkAttack();
		
		if (Rnd.nextInt(100) == 0)
		{
			_actor.doCast(SkillTable.getInstance().getInfo(4222, 1));
		}
		else if (Rnd.get(100) < 5)
		{
			_actor.doCast(SkillTable.getInstance().getInfo(4216, 1));
		}
		else if (Rnd.get(100) < 10)
		{
			_actor.doCast(SkillTable.getInstance().getInfo(4217, 1));
		}
		
		if (Rnd.get(100) < (L2Math.limit(1, 90 - (_actor.getCurrentHp() / _actor.getMaxHp() * 100), 90)))
		{
			for (int i = 0; i < MINION_IDS.length; i++)
			{
				if (Rnd.get(100) < CHANCES[i])
					spawnMinion(MINION_IDS[i]);
			}
			_lastMinionSpawn = System.currentTimeMillis();
		}
	}
	
	private void spawnMinion(int minionId)
	{
		if (System.currentTimeMillis() - _lastMinionSpawn <= 60000)
			return;
		
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(minionId);
		if (template != null)
		{
			int x = _actor.getX();
			int y = _actor.getY();
			int z = _actor.getZ();
			
			int offset = Rnd.get(2); // Get the direction of the offset
			if (offset == 0)
			{
				offset = -1;
			} // make offset negative
			offset *= Rnd.get(50, 100);
			x += offset;
			
			offset = Rnd.get(2); // Get the direction of the offset
			if (offset == 0)
			{
				offset = -1;
			} // make offset negative
			offset *= Rnd.get(50, 100);
			y += offset;
			
			L2Spawn spawn = new L2Spawn(template);
			spawn.setHeading(0);
			spawn.setLocx(x);
			spawn.setLocy(y);
			spawn.setLocz(z + 20);
			spawn.stopRespawn();
			spawn.spawnOne(false);
		}
	}
}
