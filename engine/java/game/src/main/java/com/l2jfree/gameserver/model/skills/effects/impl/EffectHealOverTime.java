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
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.packets.server.ExRegMax;

public final class EffectHealOverTime extends L2Effect
{
	public EffectHealOverTime(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.HEAL_OVER_TIME;
	}
	
	/** Notify started */
	@Override
	protected boolean onStart()
	{
		if (getEffected() instanceof L2Player)
			((L2Player)getEffected()).sendPacket(new ExRegMax(calc(), getTotalCount() * getPeriod(), getPeriod()));
		return true;
	}
	
	@Override
	protected boolean onActionTime()
	{
		if (getEffected().isDead())
			return false;
		
		if (getEffected() instanceof L2DoorInstance)
			return false;
		
		double hp = getEffected().getStatus().getCurrentHp();
		double maxhp = getEffected().getMaxHp();
		hp += calc();
		if (hp > maxhp)
		{
			hp = maxhp;
		}
		getEffected().getStatus().setCurrentHp(hp);
		return true;
	}
}
