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

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Creature.AIAccessor;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.instancemanager.grandbosses.QueenAntManager;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.util.Util;

/**
 * This class manages AI of Larva for Queen Ant raid
 * 
 * @author hex1r0
 */
public class QueenAntNurseAI extends L2AttackableAI
{
	private long _lastTargetSwitch = 0;
	
	public QueenAntNurseAI(AIAccessor accessor)
	{
		super(accessor);
	}
	
	@Override
	protected void thinkActive()
	{
		L2Npc queen = QueenAntManager.getInstance().getQueenAntInstance();
		
		if (queen != null)
		{
			checkDistance(queen);
		}
	}
	
	@Override
	protected void thinkAttack()
	{
		L2Skill healSkill = SkillTable.getInstance().getInfo(4020, 1);
		
		if (healSkill == null)
			return;
		
		L2Npc newTarget = (L2Npc)_actor.getTarget();
		
		if (System.currentTimeMillis() - _lastTargetSwitch > 30000)
		{
			newTarget = chooseTarget();
			_lastTargetSwitch = System.currentTimeMillis();
		}
		
		if (newTarget == null)
			return;
		
		if (Util.checkIfInRange(healSkill.getCastRange(), _actor, newTarget, true))
		{
			if (!_actor.isSkillDisabled(healSkill.getId()))
			{
				stopFollow();
				clientStopMoving(null);
				_actor.setTarget(newTarget);
				_actor.doCast(healSkill);
			}
		}
		else
		{
			checkDistance(newTarget);
		}
	}
	
	private L2Npc chooseTarget()
	{
		final L2Attackable queen = QueenAntManager.getInstance().getQueenAntInstance();
		final L2Attackable larva = QueenAntManager.getInstance().getLarvaInstance();
		
		if (queen == null)
			return null;
		
		if (larva == null)
			return queen;
		
		final L2Creature queenMostHated = queen.getMostHated();
		final L2Creature larvaMostHated = larva.getMostHated();
		
		if (queenMostHated == null && larvaMostHated == null)
			return null;
		else if (queenMostHated != null && larvaMostHated == null)
			return queen;
		else if (queenMostHated == null && larvaMostHated != null)
			return larva;
		else if (queen.getHating(queenMostHated) > larva.getHating(larvaMostHated))
			return queen;
		else
			return larva;
	}
	
	private void checkDistance(L2Npc npc)
	{
		if (!GeoData.getInstance().canSeeTarget(_actor, npc))
		{
			_actor.teleToLocation(npc.getX(), npc.getY(), npc.getZ());
		}
		else
		{
			_actor.setRunning();
			startFollow(npc);
		}
	}
}
