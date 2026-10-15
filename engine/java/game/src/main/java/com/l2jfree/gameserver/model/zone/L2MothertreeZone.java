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
package com.l2jfree.gameserver.model.zone;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.network.SystemMessageId;

public class L2MothertreeZone extends L2Zone
{
	@Override
	protected void onEnter(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			character.setInsideZone(FLAG_MOTHERTREE, true);
			character.sendPacket(SystemMessageId.ENTER_SHADOW_MOTHER_TREE);
		}
		
		super.onEnter(character);
	}
	
	@Override
	protected void onExit(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			character.setInsideZone(FLAG_MOTHERTREE, false);
			character.sendPacket(SystemMessageId.EXIT_SHADOW_MOTHER_TREE);
		}
		
		super.onExit(character);
	}
	
	@Override
	protected boolean checkConstantConditions(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			L2Player player = (L2Player)character;
			
			if (player.getRace() != Race.Elf)
				return false;
		}
		
		return super.checkConstantConditions(character);
	}
	
	@Override
	protected boolean checkDynamicConditions(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			L2Player player = (L2Player)character;
			
			if (player.isInParty())
				for (L2Player member : player.getParty().getPartyMembers())
					if (member.getRace() != Race.Elf)
						return false;
		}
		
		return super.checkDynamicConditions(character);
	}
}
