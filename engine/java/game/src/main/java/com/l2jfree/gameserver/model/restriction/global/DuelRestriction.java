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
package com.l2jfree.gameserver.model.restriction.global;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.DuelManager;
import com.l2jfree.gameserver.model.entity.Duel;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;

/**
 * @author NB4L1
 */
public final class DuelRestriction extends AbstractRestriction
{
	@Override
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		if (activeChar.isInDuel())
		{
			activeChar.sendMessage("You are participating in a duel!");
			return true;
		}
		
		return false;
	}
	
	@Override
	@DisabledRestriction
	public boolean canInviteToParty(L2Player activeChar, L2Player target)
	{
		if (activeChar.isInDuel() || target.isInDuel())
			return false;
		
		return true;
	}
	
	@Override
	public boolean isInvul(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		return Duel.isInvul(target, activeChar);
	}
	
	@Override
	public boolean canTeleport(L2Player activeChar)
	{
		// Check to see if player is in a duel
		if (activeChar.isInDuel())
		{
			activeChar.sendMessage("You can't teleport during a duel.");
			return false;
		}
		
		return true;
	}
	
	@Override
	public void effectCreated(L2Effect effect)
	{
		// Let the duel manager know about it, to remove it after the duel
		// so the debuff can be removed after the duel
		// (player & target must be in the same duel)
		L2Player effectedPlayer = effect.getEffected().getActingPlayer();
		
		if (effectedPlayer == null || !effectedPlayer.isInDuel() || !effect.getSkill().isOffensive())
			return;
		
		DuelManager.getInstance().onBuff(effectedPlayer, effect);
	}
}
