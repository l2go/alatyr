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
import com.l2jfree.gameserver.instancemanager.TransformationManager;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author nBd
 */
public final class EffectTransformation extends L2Effect
{
	public EffectTransformation(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	// Special constructor to steal this effect
	public EffectTransformation(Env env, L2Effect effect)
	{
		super(env, effect);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.TRANSFORMATION;
	}
	
	@Override
	protected boolean onStart()
	{
		if (!(getEffected() instanceof L2Player))
			return false;
		
		L2Player trg = (L2Player)getEffected();
		
		// No transformation if dead or cursed by cursed weapon
		if (trg.isAlikeDead() || trg.isCursedWeaponEquipped())
			return false;
		
		if (trg.getTransformation() != null)
		{
			trg.sendPacket(SystemMessageId.YOU_ALREADY_POLYMORPHED_AND_CANNOT_POLYMORPH_AGAIN);
			return false;
		}
		
		TransformationManager.getInstance().transformPlayer(getSkill().getTransformId(), trg);
		return true;
	}
	
	@Override
	protected void onExit()
	{
		getEffected().stopTransformation(false);
	}
}
