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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.packets.server.DeleteObject;

/**
 * @author ZaKaX - nBd
 */
public class EffectHide extends L2Effect
{
	public EffectHide(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	public EffectHide(Env env, L2Effect effect)
	{
		super(env, effect);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.HIDE;
	}
	
	@Override
	protected boolean onStart()
	{
		if (getEffected() instanceof L2Player)
		{
			L2Player activeChar = ((L2Player)getEffected());
			activeChar.getAppearance().setInvisible();
			
			if (activeChar.getAI().getNextCtrlIntention() == CtrlIntention.AI_INTENTION_ATTACK)
				activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			
			final DeleteObject del = new DeleteObject(activeChar);
			for (L2Creature obj : activeChar.getKnownList().getKnownCharacters())
			{
				if (obj == null)
					continue;
				
				if (obj.getTarget() == activeChar)
				{
					obj.setTarget(null);
					obj.abortAttack();
					obj.abortCast();
					obj.getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				}
				
				if (obj instanceof L2Player)
					((L2Player)obj).sendPacket(del);
			}
		}
		
		return true;
	}
	
	@Override
	protected void onExit()
	{
		if (getEffected() instanceof L2Player)
		{
			L2Player activeChar = ((L2Player)getEffected());
			activeChar.getAppearance().setVisible();
		}
	}
	
	@Override
	protected int getTypeBasedAbnormalEffect()
	{
		return AbnormalEffect.STEALTH.getMask();
	}
}
