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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.packets.server.Die;

/**
 * @author savormix
 */
public class EffectEradication extends L2Effect
{
	public EffectEradication(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.L2Effect#getEffectType()
	 */
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.DEBUFF;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.L2Effect#onStart()
	 */
	@Override
	protected boolean onStart()
	{
		getEffected().setIsEradicated(true);
		return true;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.model.L2Effect#onExit()
	 */
	@Override
	protected void onExit()
	{
		L2Creature actor = getEffected();
		actor.setIsEradicated(false);
		if (actor.isDead() && actor.getActingPlayer() != null)
			actor.getActingPlayer().sendPacket(new Die(actor));
	}
}
