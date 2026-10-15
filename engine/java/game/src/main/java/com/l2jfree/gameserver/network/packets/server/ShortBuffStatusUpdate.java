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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class ShortBuffStatusUpdate extends L2ServerPacket
{
	private static final String _S__F4_SHORTBUFFSTATUSUPDATE = "[S] F4 ShortBuffStatusUpdate";
	
	private final int _skillId;
	private final int _skillLvl;
	private final int _duration;
	
	public ShortBuffStatusUpdate(int skillId, int skillLvl, int duration)
	{
		_skillId = skillId;
		_skillLvl = skillLvl;
		_duration = duration;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfa);
		writeD(_skillId);
		writeD(_skillLvl);
		writeD(_duration);
	}
	
	@Override
	public String getType()
	{
		return _S__F4_SHORTBUFFSTATUSUPDATE;
	}
	
	public static boolean isShortBuff(L2Effect effect)
	{
		if (effect == null)
			return false;
		
		switch (effect.getId())
		{
			case 2031:
			case 2032:
			case 2037:
			case 26025:
			case 26026:
				return true;
			default:
				return false;
		}
	}
	
	public static int getPriority(L2Effect effect)
	{
		if (effect == null)
			return 0;
		
		switch (effect.getId())
		{
		// lesser healing potions
			case 2031:
				return 1;
				// healing potions
			case 2032:
			case 26026:
				return 2;
				// greater healing potions
			case 2037:
			case 26025:
				return 3;
			default:
				throw new IllegalStateException();
		}
	}
}
