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
package com.l2jfree.gameserver.instancemanager;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.CrownTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.items.L2ItemInstance;

/**
 * @author evill33t, NB4L1
 */
public final class CrownManager
{
	private static final Logger _log = LoggerFactory.getLogger(CrownManager.class);
	
	static
	{
		_log.info("CrownManager: Initialized.");
	}
	
	public static void checkCrowns(L2Clan clan)
	{
		if (clan == null)
			return;
		
		for (L2ClanMember member : clan.getMembers())
			if (member != null && member.isOnline())
				checkCrowns(member.getPlayerInstance());
	}
	
	public static void checkCrowns(L2Player activeChar)
	{
		if (activeChar == null)
			return;
		
		int crownId = -1;
		boolean isLeader = false;
		
		final L2Clan clan = activeChar.getClan();
		
		if (clan != null)
		{
			final Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
			
			if (castle != null)
				crownId = CrownTable.getCrownId(castle.getCastleId());
			
			if (clan.getLeaderId() == activeChar.getObjectId())
				isLeader = true;
		}
		
		boolean alreadyFoundCirclet = false;
		boolean alreadyFoundCrown = false;
		
		for (L2ItemInstance item : activeChar.getInventory().getItems())
		{
			if (ArrayUtils.contains(CrownTable.getCrownIds(), item.getItemId()))
			{
				if (crownId > 0)
				{
					if (item.getItemId() == crownId)
					{
						if (!alreadyFoundCirclet)
						{
							alreadyFoundCirclet = true;
							continue;
						}
					}
					else if (item.getItemId() == 6841 && isLeader)
					{
						if (!alreadyFoundCrown)
						{
							alreadyFoundCrown = true;
							continue;
						}
					}
				}
				
				//WRONG! The crown is not sellable/tradeable/dropable
				//And the circlets are sellable!!!, but not tradeable or dropable
				//Unequip is what happens
				if (item.getItemId() == 6841 || Config.ALT_REMOVE_CASTLE_CIRCLETS)
					activeChar.destroyItem("Removing Crown", item, activeChar, true);
				else if (item.isEquipped())
					activeChar.getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_HAIR2);
				
				// No need to update every item in the inventory
				//activeChar.getInventory().updateDatabase();
			}
		}
	}
}
