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
package com.l2jfree.gameserver.model.skills.conditions;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.impl.EffectFusion;

/**
 * @author kombat, Forsaiken
 */
final class ConditionForceBuff extends Condition
{
	private static final short BATTLE_FORCE = 5104;
	private static final short SPELL_FORCE = 5105;
	
	private final byte _battleForces;
	private final byte _spellForces;
	
	public ConditionForceBuff(byte battleForces, byte spellForces)
	{
		_battleForces = battleForces;
		_spellForces = spellForces;
	}
	
	@Override
	boolean testImpl(Env env)
	{
		L2Player player = env.player.getActingPlayer();
		
		if (player.isGM() && player.getActiveClass() == player.getBaseClass())
			return true;
		
		if (_battleForces > 0)
		{
			L2Effect force = player.getFirstEffect(BATTLE_FORCE);
			
			if (!(force instanceof EffectFusion) || ((EffectFusion)force)._effect < _battleForces)
				return false;
		}
		
		if (_spellForces > 0)
		{
			L2Effect force = player.getFirstEffect(SPELL_FORCE);
			
			if (!(force instanceof EffectFusion) || ((EffectFusion)force)._effect < _spellForces)
				return false;
		}
		
		return true;
	}
}
