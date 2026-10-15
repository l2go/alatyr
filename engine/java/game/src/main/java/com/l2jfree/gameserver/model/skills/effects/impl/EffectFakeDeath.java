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
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author mkizub
 */
public final class EffectFakeDeath extends L2Effect
{
	
	public EffectFakeDeath(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.FAKE_DEATH;
	}
	
	/** Notify started */
	@Override
	protected boolean onStart()
	{
		getEffected().startFakeDeath();
		return true;
	}
	
	/** Notify exited */
	@Override
	protected void onExit()
	{
		getEffected().stopFakeDeath(false);
	}
	
	@Override
	protected boolean onActionTime()
	{
		if (getEffected().isDead())
			return false;
		
		/*
		 * for (L2Object obj :
		 * getEffected().getKnownList().getKnownCharacters()) {
		 * 
		 * if ((obj != null) && (obj instanceof L2MonsterInstance || obj
		 * instanceof L2SiegeGuardInstance || obj instanceof L2GuardInstance ))
		 * continue;
		 * 
		 * if (((L2Npc)obj).getTarget() == getEffected() &&
		 * (!((L2Npc)obj) instanceof L2DoorInstance) &&
		 * ((L2Npc)obj).getTarget() != null &&
		 * !((L2Npc)obj).isDead()) {
		 * ((L2Npc)obj).setTarget(null);
		 * ((L2Npc)obj).getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, //
		 * Move Npc to Spawn Location new
		 * L2CharPosition(((L2Npc)obj).getSpawn().getLocx(),
		 * ((L2Npc)obj).getSpawn().getLocy(),
		 * ((L2Npc)obj).getSpawn().getLocz(),0)); } }
		 * 
		 * if (!((L2Player)obj).isDead() && ((L2Player)obj) != null &&
		 * ((L2Player)obj) != getEffected() &&
		 * ((L2Player)obj).isInsideRadius(getEffected(),130,true,false))
		 * //check if PC you Train is Close to you { if
		 * (((L2Player)obj).isMoving() && (NPC.getTemplate().aggroRange > 0) &&
		 * (Rnd.get(100) < 75)); //If PC is moving give a chance to move agrro
		 * mobs on him. { NPC.setTarget(((L2Player)obj));
		 * NPC.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK); //Train
		 * him! } }
		 */
		
		double manaDam = calc();
		
		if (manaDam > getEffected().getStatus().getCurrentMp())
		{
			if (getSkill().isToggle())
			{
				getEffected().sendPacket(SystemMessageId.SKILL_REMOVED_DUE_LACK_MP);
				return false;
			}
		}
		
		getEffected().reduceCurrentMp(manaDam);
		return true;
	}
}
