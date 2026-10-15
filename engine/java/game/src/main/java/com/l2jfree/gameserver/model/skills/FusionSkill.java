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
package com.l2jfree.gameserver.model.skills;

import java.util.concurrent.Future;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.model.skills.effects.impl.EffectFusion;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillFusion;
import com.l2jfree.gameserver.util.Util;

/**
 * @author kombat/crion
 */
public final class FusionSkill implements Runnable
{
	private final L2Creature _caster;
	private final L2Creature _target;
	private final L2SkillFusion _skill;
	
	private final Future<?> _geoCheckTask;
	
	public L2Creature getTarget()
	{
		return _target;
	}
	
	public FusionSkill(L2Creature caster, L2Creature target, L2SkillFusion skill)
	{
		_caster = caster;
		_target = target;
		_skill = skill;
		
		EffectFusion effect = getFusionTriggeredEffect();
		
		if (effect != null)
			effect.increaseEffect();
		
		else
			skill.getFusionTriggeredSkill().getEffects(_caster, _target);
		
		_geoCheckTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(this, 1000, 1000);
	}
	
	private EffectFusion getFusionTriggeredEffect()
	{
		return (EffectFusion)_target.getFirstEffect(_skill.getFusionTriggeredSkill().getId());
	}
	
	public void onCastAbort()
	{
		_caster.setFusionSkill(null);
		
		EffectFusion effect = getFusionTriggeredEffect();
		if (effect != null)
			effect.decreaseEffect();
		
		_geoCheckTask.cancel(true);
	}
	
	@Override
	public void run()
	{
		if (!Util.checkIfInRange(_skill.getCastRange(), _caster, _target, true))
			_caster.abortCast();
		
		else if (!GeoData.getInstance().canSeeTarget(_caster, _target))
			_caster.abortCast();
	}
}
