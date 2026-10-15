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

import com.l2jfree.gameserver.datatables.SkillTable.SkillInfo;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public final class ItemSkills implements IItemHandler
{
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		L2Player activeChar = playable.getActingPlayer();
		boolean isPet = playable instanceof L2PetInstance;
		
		if (activeChar.isInOlympiadMode())
		{
			activeChar.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			return;
		}
		
		activeChar.getEffects().dispelOnAction();
		
		for (SkillInfo skillInfo : item.getEtcItem().getSkillInfos())
		{
			L2Skill itemSkill = skillInfo.getSkill();
			if (itemSkill == null)
				continue;
			
			int skillId = skillInfo.getId();
			
			if (!itemSkill.checkCondition(playable, playable.getTarget()))
				return;
			
			if (playable.isSkillDisabled(skillId))
			{
				activeChar.sendReuseMessage(itemSkill);
				return;
			}
			
			// pets can use items only when they are tradeable
			if (isPet && !item.isTradeable())
				activeChar.sendPacket(SystemMessageId.ITEM_NOT_FOR_PETS);
			else
			{
				// send message to owner
				if (isPet)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.PET_USES_S1);
					sm.addSkillName(itemSkill);
					activeChar.sendPacket(sm);
				}
				
				if (itemSkill.isPotion())
				{
					playable.doSimultaneousCast(itemSkill);
					// Summons should be affected by herbs too, self time effect is handled at L2Effect constructor
					if (!isPet && item.getItemType() == L2EtcItemType.HERB
							&& activeChar.getPet() instanceof L2SummonInstance)
						activeChar.getPet().doSimultaneousCast(itemSkill);
				}
				else
				{
					// seems a more logical way to call skills, as it contains more checks, like isCastingNow()
					playable.useMagic(itemSkill, false, false);
					// playable.stopMove(null);
					// if (!playable.isCastingNow())
					// playable.doCast(itemSkill);
				}
				
				activeChar.disableSkill(skillId, itemSkill.getReuseDelay());
			}
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return null;
	}
}
