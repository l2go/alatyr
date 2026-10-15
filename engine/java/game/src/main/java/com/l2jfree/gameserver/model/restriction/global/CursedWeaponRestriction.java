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
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author NB4L1
 */
public final class CursedWeaponRestriction extends AbstractRestriction
{
	@Override
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		if (activeChar.isCursedWeaponEquipped())
		{
			activeChar.sendMessage("You are holding a cursed weapon!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean canInviteToParty(L2Player activeChar, L2Player target)
	{
		if (activeChar.isCursedWeaponEquipped() || target.isCursedWeaponEquipped())
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return false;
		}
		
		return true;
	}
	
	@Override
	public boolean isProtected(L2Creature activeChar, L2Creature target, L2Skill skill, boolean sendMessage,
			L2Player attacker_, L2Player target_, boolean isOffensive)
	{
		if (attacker_ == null || target_ == null || attacker_ == target_)
			return false;
		
		if (target_.isCursedWeaponEquipped() && attacker_.getLevel() < 21)
		{
			if (sendMessage)
				attacker_.sendMessage("You can't attack a cursed player while you are under level 21.");
			return true;
		}
		else if (attacker_.isCursedWeaponEquipped() && target_.getLevel() < 21)
		{
			if (sendMessage)
				attacker_.sendMessage("You can't attack a newbie player while you are holding a cursed weapon.");
			return true;
		}
		
		return false;
	}
	
	@Override
	public void playerLoggedIn(L2Player activeChar)
	{
		if (activeChar.isCursedWeaponEquipped())
			CursedWeaponsManager.getInstance().getCursedWeapon(activeChar.getCursedWeaponEquippedId()).cursedOnLogin();
	}
	
	@Override
	public void playerDisconnected(L2Player activeChar)
	{
		if (activeChar.isCursedWeaponEquipped())
			CursedWeaponsManager.getInstance().onExit(activeChar);
	}
}
