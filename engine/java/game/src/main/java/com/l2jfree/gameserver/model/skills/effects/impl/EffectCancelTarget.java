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

import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;

/**
 * @author decad Implementation of the CANCEL TARGET
 */
public final class EffectCancelTarget extends L2Effect
{
	
	public EffectCancelTarget(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.CANCEL_TARGET;
	}
	
	/** Notify started */
	@Override
	protected boolean onStart()
	{
		getEffected().getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		getEffected().setTarget(null);
		getEffected().breakAttack();
		getEffected().breakCast();
		getEffected().abortAttack();
		getEffected().abortCast();
		return true;
	}
}
