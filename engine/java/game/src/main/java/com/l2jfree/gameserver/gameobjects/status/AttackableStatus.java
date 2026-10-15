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
package com.l2jfree.gameserver.gameobjects.status;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2MinionInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;

/**
 * @author NB4L1
 */
public class AttackableStatus extends NpcStatus
{
	public AttackableStatus(L2Attackable activeChar)
	{
		super(activeChar);
	}
	
	@Override
	public L2Attackable getActiveChar()
	{
		return (L2Attackable)_activeChar;
	}
	
	@Override
	void reduceHp0(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		synchronized (getActiveChar())
		{
			if (!getActiveChar().isDead())
				reduceAttackableHp(value, attacker, awake, isDOT, isConsume);
		}
	}

	private void reduceAttackableHp(double value, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		getActiveChar().recordHuntDamage(attacker, value, getCurrentHp());
		/*
		if ((this instanceof L2SiegeGuardInstance) && (attacker instanceof L2SiegeGuardInstance))
		    //if((this.getEffect(L2EffectType.CONFUSION)!=null) && (attacker.getEffect(L2EffectType.CONFUSION)!=null))
		        return;
		
		if ((this instanceof L2MonsterInstance)&&(attacker instanceof L2MonsterInstance))
		    if((this.getEffect(L2EffectType.CONFUSION)!=null) && (attacker.getEffect(L2EffectType.CONFUSION)!=null))
		        return;
		*/
		
		getActiveChar().startCommandChannelTimer(attacker);
		
		// Keep quest notifications separate from actual HP credit
		if (attacker != null)
			getActiveChar().addDamage(attacker, (int)value);
		
		// If this L2Attackable is a L2MonsterInstance and it has spawned minions, call its minions to battle
		if (getActiveChar() instanceof L2MonsterInstance)
		{
			L2MonsterInstance master = (L2MonsterInstance)getActiveChar();
			if (getActiveChar() instanceof L2MinionInstance)
			{
				master = ((L2MinionInstance)getActiveChar()).getLeader();
				if (master != null && !master.isInCombat() && !master.isDead())
				{
					master.addDamage(attacker, 1, null);
					master.callMinionsToAssist(attacker);
				}
			}
			else if (master.hasMinions())
				master.callMinionsToAssist(attacker);
		}
		
		if (value > 0)
		{
			if (getActiveChar().isOverhit())
				getActiveChar().setOverhitValues(attacker, value);
			else
				getActiveChar().overhitEnabled(false);
		}
		else
			getActiveChar().overhitEnabled(false);
		
		// Reduce the current HP of the L2Attackable and launch the doDie Task if necessary
		super.reduceHp0(value, attacker, awake, isDOT, isConsume);
		
		if (!getActiveChar().isDead())
			// If the attacker's hit didn't kill the mob, clear the over-hit flag
			getActiveChar().overhitEnabled(false);
	}
}
