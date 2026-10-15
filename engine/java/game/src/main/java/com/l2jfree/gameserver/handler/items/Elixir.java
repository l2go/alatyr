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
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExUseSharedGroupItem;

public class Elixir implements IItemHandler
{
	private static final int[] ITEM_IDS = { 8622, 8623, 8624, 8625, 8626, 8627, 8628, 8629, 8630, 8631, 8632, 8633,
			8634, 8635, 8636, 8637, 8638, 8639, 20354, 20355, 20356, 20357, 20358, 20359, 20360, 20361, 20362, 20363,
			22027, 22028, 22029, 22030, 22031, 22032, 22033, 22034, 22035, 22036 };
	
	/**
	 * 
	 * @see com.l2jfree.gameserver.handler.IItemHandler#useItem(com.l2jfree.gameserver.gameobjects.L2Playable, com.l2jfree.gameserver.model.items.L2ItemInstance)
	 */
	@Override
	public synchronized void useItem(L2Playable playable, L2ItemInstance item)
	{
		L2Player activeChar; // use activeChar only for L2Player checks where cannot be used PetInstance
		if (playable instanceof L2Player)
			activeChar = (L2Player)playable;
		else
		{
			playable.sendPacket(SystemMessageId.ITEM_NOT_FOR_PETS);
			return;
		}
		
		if (activeChar.isInOlympiadMode())
		{
			activeChar.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			return;
		}
		
		if (playable.isAllSkillsDisabled())
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		activeChar.getEffects().dispelOnAction();
		
		int itemId = item.getItemId();
		
		switch (itemId)
		{
			case 8622:
			case 8623:
			case 8624:
			case 8625:
			case 8626:
			case 8627:
				if (usePotion(activeChar, 2287, 2288, itemId - 8621))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 1, 300000, 300000));
				break;
			case 8628:
			case 8629:
			case 8630:
			case 8631:
			case 8632:
			case 8633:
				if (usePotion(activeChar, 2288, 2287, itemId - 8627))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 1, 300000, 300000));
				break;
			case 8634:
			case 8635:
			case 8636:
			case 8637:
			case 8638:
			case 8639:
				if (usePotion(activeChar, 2289, 0, itemId - 8633))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 2, 300000, 300000));
				break;
			case 22027:
			case 22028:
			case 22029:
			case 22030:
			case 22031:
				if (usePotion(activeChar, 26027, 0, itemId - 22025))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 2, 300000, 300000));
				break;
			case 22032:
			case 22033:
			case 22034:
			case 22035:
			case 22036:
				if (usePotion(activeChar, 26028, 0, itemId - 22030))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 1, 300000, 300000));
				break;
			case 20354:
			case 20355:
			case 20356:
			case 20357:
			case 20358:
				if (usePotion(activeChar, 22043, 0, itemId - 20352))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 2, 300000, 300000));
				break;
			case 20359:
			case 20360:
			case 20361:
			case 20362:
			case 20363:
				if (usePotion(activeChar, 22044, 0, itemId - 20357))
					activeChar.sendPacket(new ExUseSharedGroupItem(itemId, 1, 300000, 300000));
				break;
		}
	}
	
	/**
	 * 
	 * @param activeChar
	 * @param magicId
	 * @param level
	 * @return
	 */
	public boolean usePotion(L2Player activeChar, int magicId, int skillToCheck, int level)
	{
		boolean check = skillToCheck != 0 ? true : false;
		L2Skill skill = SkillTable.getInstance().getInfo(magicId, level);
		if (skill != null)
		{
			if (!skill.checkCondition(activeChar, activeChar))
				return false;
			// Return false if potion is in reuse
			// so is not destroyed from inventory
			if (activeChar.isSkillDisabled(skill.getId()))
			{
				activeChar.sendReuseMessage(skill);
				return false;
			}
			if (check)
			{
				if (activeChar.isSkillDisabled(skillToCheck))
				{
					activeChar.sendReuseMessage(skill);
					return false;
				}
				activeChar.disableSkill(skillToCheck, skill.getReuseDelay());
			}
			activeChar.doSimultaneousCast(skill);
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @see com.l2jfree.gameserver.handler.IItemHandler#getItemIds()
	 */
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
