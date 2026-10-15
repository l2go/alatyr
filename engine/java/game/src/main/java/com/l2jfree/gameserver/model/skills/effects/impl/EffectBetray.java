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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;

/**
 * @author decad
 */
public class EffectBetray extends L2Effect
{
	public EffectBetray(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	/**
	 * @see com.l2jfree.gameserver.model.skills.effects.L2Effect#getEffectType()
	 */
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.BETRAY;
	}
	
	/**
	 * @see com.l2jfree.gameserver.model.skills.effects.L2Effect#onStart()
	 */
	@Override
	protected boolean onStart()
	{
		if (getEffector() instanceof L2Player && getEffected() instanceof L2Summon)
		{
			L2Player targetOwner = getEffected().getActingPlayer();
			getEffected().getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, targetOwner);
			getEffected().setIsBetrayed(true);
			if (targetOwner != null)
				targetOwner.setIsBetrayed(true);
			return true;
		}
		return false;
	}
	
	/**
	 * @see com.l2jfree.gameserver.model.skills.effects.L2Effect#onExit()
	 */
	@Override
	protected void onExit()
	{
		getEffected().setIsBetrayed(false);
		getEffected().getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		L2Player targetOwner = getEffected().getActingPlayer();
		if (targetOwner != null)
			targetOwner.setIsBetrayed(false);
	}
}
