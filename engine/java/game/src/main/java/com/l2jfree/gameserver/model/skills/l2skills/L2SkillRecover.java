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
package com.l2jfree.gameserver.model.skills.l2skills;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * For skills like "Blessing of Eva". Each part is <U>optional</U>.
 * <LI>Restores CP (may not specify)</LI>
 * <LI>Restores HP (may not specify)</LI>
 * <LI>Restores MP (may not specify)</LI>
 * <LI>Cancels bad effects (power = chance, set 0 to disable)</LI>
 * 
 * @author Savormix
 */
public class L2SkillRecover extends L2Skill
{
	private final double _cp;
	private final double _hp;
	private final double _mp;
	private final double _power;
	
	public L2SkillRecover(StatsSet set)
	{
		super(set);
		
		_cp = set.getFloat("restoredCP", 0);
		_hp = set.getFloat("restoredHP", 0);
		_mp = set.getFloat("restoredMP", 0);
		_power = Math.min(95, getPower());
	}
	
	public void recover(L2Creature target)
	{
		if (_cp == -1)
			target.getStatus().setCurrentCp(target.getStat().getMaxCp());
		else if (_cp > 0)
			target.getStatus().setCurrentCp(target.getStatus().getCurrentCp() + _cp);
		
		if (_hp == -1)
			target.getStatus().setCurrentHp(target.getStat().getMaxHp());
		else if (_hp > 0)
			target.getStatus().increaseHp(_hp);
		
		if (_mp == -1)
			target.getStatus().setCurrentMp(target.getStat().getMaxMp());
		else if (_mp > 0)
			target.getStatus().increaseMp(_mp);
		
		if (_power <= 0)
			return; //do not negate anything
			
		for (L2Effect e : target.getAllEffects())
			e.tryNegateDebuff((int)_power);
	}
}
