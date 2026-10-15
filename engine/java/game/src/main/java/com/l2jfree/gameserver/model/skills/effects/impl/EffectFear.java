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
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2FortCommanderInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortSiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeFlagInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeSummonInstance;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.templates.EffectTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;

/**
 * @author littlecrow Implementation of the Fear Effect
 */
public final class EffectFear extends L2Effect
{
	public static final int FEAR_RANGE = 500;
	
	private int _dX = -1;
	private int _dY = -1;
	
	public EffectFear(Env env, EffectTemplate template)
	{
		super(env, template);
	}
	
	@Override
	public L2EffectType getEffectType()
	{
		return L2EffectType.FEAR;
	}
	
	/** Notify started */
	@Override
	protected boolean onStart()
	{
		// Fear skills cannot be used by L2Player to L2Player.
		// Heroic Dread, Curse: Fear, Fear, Horror, Sword Symphony, Word of Fear and Mass Curse Fear are the exceptions.
		if (getEffected() instanceof L2Player && getEffector() instanceof L2Player)
		{
			switch (getSkill().getId())
			{
				case 65:
				case 98:
				case 1092:
				case 1169:
				case 1272:
				case 1376:
				case 1381:
					// all ok
					break;
				default:
					return false;
			}
		}
		
		if (getEffected() instanceof L2NpcInstance || getEffected() instanceof L2SiegeGuardInstance
				|| getEffected() instanceof L2FortSiegeGuardInstance
				|| getEffected() instanceof L2FortCommanderInstance || getEffected() instanceof L2SiegeFlagInstance
				|| getEffected() instanceof L2SiegeSummonInstance)
			return false;
		
		if (!getEffected().isAfraid())
		{
			if (getEffected().getX() > getEffector().getX())
				_dX = 1;
			if (getEffected().getY() > getEffector().getY())
				_dY = 1;
			
			getEffected().startFear();
			onActionTime();
			return true;
		}
		return false;
	}
	
	/** Notify exited */
	@Override
	protected void onExit()
	{
		getEffected().stopFear(false);
	}
	
	@Override
	protected boolean onActionTime()
	{
		int posX = getEffected().getX();
		int posY = getEffected().getY();
		int posZ = getEffected().getZ();
		
		posX += _dX * FEAR_RANGE;
		posY += _dY * FEAR_RANGE;
		
		Location destiny =
				GeoData.getInstance().moveCheck(getEffected().getX(), getEffected().getY(), getEffected().getZ(), posX,
						posY, posZ, getEffected().getInstanceId());
		if (!(getEffected() instanceof L2PetInstance))
			getEffected().setRunning();
		getEffected().getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(destiny));
		return true;
	}
}
