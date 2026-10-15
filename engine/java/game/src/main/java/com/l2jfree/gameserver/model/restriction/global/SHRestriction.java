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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.SH;

public final class SHRestriction extends AbstractRestriction
{
	private static final class SingletonHolder
	{
		private static final SHRestriction INSTANCE = new SHRestriction();
	}
	
	public static SHRestriction getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private SHRestriction()
	{
	}
	
	@Override
	public boolean onAction(L2Npc npc, L2Player activeChar)
	{
		if (npc._isEventMobSH)
		{
			SH.showEventHtml(activeChar, String.valueOf(npc.getObjectId()));
			return true;
		}
		
		return false;
	}
}
