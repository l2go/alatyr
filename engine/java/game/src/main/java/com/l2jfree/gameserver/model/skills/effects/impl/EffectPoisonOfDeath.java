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

import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;

/**
 * Poison of death used by Witch Kalis in the "Proof of Clan Alliance"
 * quest. For an hour, it deals constant damage (around 50HP/tick), after
 * that it begins to speed up, eventually even a team of healers cannot
 * outheal the affected leader.
 * @author savormix
 */
public class EffectPoisonOfDeath extends EffectRoot
{
	private static final int CONSTANT_DAMAGE = 3600;
	private static final double DAMAGE_GROWTH = 2;
	
	public EffectPoisonOfDeath(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.L2Effect#getEffectType()
	 */
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.DMG_OVER_TIME;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.L2Effect#onActionTime()
	 */
	@Override
	protected boolean onActionTime()
	{
		if (getEffected().isDead())
			return false;
		
		double damage = calc();
		long time = getElapsedTaskTime() - CONSTANT_DAMAGE;
		if (time > 0)
			damage += time * DAMAGE_GROWTH;
		getEffected().reduceCurrentHpByDOT(damage, getEffector(), getSkill());
		return true;
	}
}
