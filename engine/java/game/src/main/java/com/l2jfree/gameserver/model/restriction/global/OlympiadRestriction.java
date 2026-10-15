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
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.handler.items.Potions;
import com.l2jfree.gameserver.handler.items.SummonItems;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.network.SystemMessageId;

/**
 * @author NB4L1
 */
public final class OlympiadRestriction extends AbstractRestriction
{
	@Override
	public boolean isRestricted(L2Player activeChar, Class<? extends GlobalRestriction> callingRestriction)
	{
		// TODO: merge different checking methods to one
		if (activeChar.isInOlympiadMode() || Olympiad.getInstance().isRegistered(activeChar)
				|| activeChar.getOlympiadGameId() != -1)
		{
			activeChar.sendMessage("You are registered on Grand Olympiad Games!");
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean canInviteToParty(L2Player activeChar, L2Player target)
	{
		if (activeChar.isInOlympiadMode() || target.isInOlympiadMode())
			return false;
		
		return true;
	}
	
	@Override
	public boolean canUseItemHandler(Class<? extends IItemHandler> clazz, int itemId, L2Playable activeChar,
			L2ItemInstance item, L2Player player)
	{
		if (clazz == SummonItems.class)
		{
			if (player != null && player.isInOlympiadMode())
			{
				player.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
				return false;
			}
		}
		else if (clazz == Potions.class)
		{
			if (player != null && player.isInOlympiadMode())
			{
				player.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public boolean canTarget(L2Creature activeChar, L2Creature target, boolean sendMessage, L2Player attacker_,
			L2Player target_)
	{
		if (attacker_ == null || target_ == null || attacker_ == target_ || attacker_.isGM())
			return true;
		
		if (attacker_.isInOlympiadMode() != target_.isInOlympiadMode())
			return false;
		
		if (attacker_.isInOlympiadMode() && target_.isInOlympiadMode())
		{
			if (attacker_.getOlympiadGameId() != target_.getOlympiadGameId())
				return false;
			
			if (!attacker_.isOlympiadStart() || !target_.isOlympiadStart())
				return false;
		}
		
		return true;
	}
	
	@Override
	public void playerDisconnected(L2Player activeChar)
	{
		if (activeChar.isInOlympiadMode())
			Olympiad.getInstance().unRegisterNoble(activeChar);
		
		// handle removal from olympiad game
		if (Olympiad.getInstance().isRegistered(activeChar) || activeChar.getOlympiadGameId() != -1)
			Olympiad.getInstance().removeDisconnectedCompetitor(activeChar);
	}
}
