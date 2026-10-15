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
package com.l2jfree.gameserver.handler.skills;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.tools.random.Rnd;

/**
 * @author hex1r0
 */
public class ZakenTeleport implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = { L2SkillType.ZAKEN_TELEPORT };
	
	@Override
	public void useSkill(L2Creature activeChar, L2Skill skill0, L2Creature... targets)
	{
		for (L2Creature c : activeChar.getKnownList().getKnownCharacters())
		{
			c.abortAttack();
			c.setTarget(null);
			c.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		teleport(targets);
		activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		if (activeChar instanceof L2MonsterInstance)
			((L2MonsterInstance)activeChar).clearAggroList();
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
	
	protected void teleport(L2Creature... targets)
	{
		final int loc[][] =
				{ { 54228, 220136, -3496 }, { 56315, 220127, -3496 }, { 56285, 218078, -3496 },
						{ 54238, 218066, -3496 }, { 55259, 219107, -3496 }, { 56295, 218078, -3224 },
						{ 56283, 220133, -3224 }, { 54241, 220127, -3224 }, { 54238, 218077, -3224 },
						{ 55268, 219090, -3224 }, { 56284, 218078, -2952 }, { 54252, 220135, -2952 },
						{ 54244, 218095, -2952 }, { 55270, 219086, -2952 } };
		
		for (L2Creature target : targets)
		{
			if (target == null)
				continue;
			
			target.abortAttack();
			target.setTarget(null);
			
			int location = Rnd.get(14);
			int x = Rnd.get(-400, 400);
			int y = Rnd.get(-400, 400);
			target.teleToLocation(loc[location][0] + x, loc[location][1] + y, loc[location][2]);
		}
	}
}
