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
package com.l2jfree.gameserver.model.skills.effects.impl;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.tools.random.Rnd;

/**
 * Implementation of the confusion effect
 * 
 * @author littlecrow
 */
public final class EffectConfusion extends L2Effect
{
	public EffectConfusion(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.CONFUSION;
	}
	
	@Override
	protected boolean onStart()
	{
		getEffected().startConfused();
		onActionTime();
		return true;
	}
	
	@Override
	protected void onExit()
	{
		getEffected().stopConfused(false);
	}
	
	@Override
	protected boolean onActionTime()
	{
		List<L2Creature> targetList = new ArrayList<L2Creature>();
		
		// Getting the possible targets
		for (L2Object obj : getEffected().getKnownList().getKnownObjects().values())
		{
			if (obj instanceof L2Creature && obj != getEffected()) // TODO: more check
				targetList.add((L2Creature)obj);
		}
		
		// if there is no target, exit function
		if (targetList.isEmpty())
			return true;
		
		// Choosing randomly a new target
		L2Object target = targetList.get(Rnd.get(targetList.size()));
		
		// Attacking the target
		getEffected().setTarget(target);
		getEffected().getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, target);
		
		return true;
	}
}
