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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;

public final class EffectGrow extends EffectBuff
{
	public EffectGrow(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	protected boolean onStart()
	{
		if (getEffected() instanceof L2Npc)
		{
			//L2Npc npc = (L2Npc) getEffected();
			//TODO: Uncomment lines when fix for mobs falling underground is found
			//npc.setCollisionHeight((int) (npc.getTemplate().getCollisionHeight() * 1.24));
			//npc.setCollisionRadius((int) (npc.getTemplate().getCollisionRadius() * 1.19));
			return true;
		}
		return false;
	}
	
	@Override
	protected void onExit()
	{
		//L2Npc npc = (L2Npc) getEffected();
		//TODO: Uncomment lines when fix for mobs falling underground is found
		//npc.setCollisionHeight(npc.getTemplate().getCollisionHeight());
		//npc.setCollisionRadius(npc.getTemplate().getCollisionRadius());
	}
	
	@Override
	protected int getTypeBasedAbnormalEffect()
	{
		return AbnormalEffect.GROW.getMask();
	}
}
