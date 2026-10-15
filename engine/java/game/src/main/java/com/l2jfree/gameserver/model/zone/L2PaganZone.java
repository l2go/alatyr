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
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

public class L2PaganZone extends L2Zone
{
	private static final int MARK = 8064;
	private static final int FADED_MARK = 8065;
	
	@Override
	protected void onEnter(L2Creature character)
	{
		if (character instanceof L2Player)
		{
			if (character.destroyItemByItemId("Pagan Zone", MARK, 1, character, false))
			{
				character.getInventory().addItem("Pagan Zone", FADED_MARK, 1, null, character);
				character.getActingPlayer().sendPacket(
						new SystemMessage(SystemMessageId.EARNED_S1).addItemName(FADED_MARK));
			}
		}
	}
}
