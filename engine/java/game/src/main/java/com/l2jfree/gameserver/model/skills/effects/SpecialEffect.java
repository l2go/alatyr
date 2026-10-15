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
package com.l2jfree.gameserver.model.skills.effects;

import java.util.NoSuchElementException;

/**
 * @author NB4L1
 */
public enum SpecialEffect
{
	S_INVULNERABLE("invulnerable", 0x000001),
	S_AIR_STUN("redglow", 0x000002),
	S_AIR_ROOT("redglow2", 0x000004),
	S_BAGUETTE_SWORD("baguettesword", 0x000008),
	S_YELLOW_AFFRO("yellowafro", 0x000010),
	S_PINK_AFFRO("pinkafro", 0x000020),
	S_BLACK_AFFRO("blackafro", 0x000040),
	S_UNKNOWN8("unknown8", 0x000080),
	S_UNKNOWN9("unknown9", 0x000100);
	
	private final int _mask;
	private final String _name;
	
	private SpecialEffect(String name, int mask)
	{
		_name = name;
		_mask = mask;
	}
	
	public final int getMask()
	{
		return _mask;
	}
	
	public final String getName()
	{
		return _name;
	}
	
	public static SpecialEffect getByName(String name)
	{
		for (SpecialEffect eff : SpecialEffect.values())
			if (eff.getName().equals(name))
				return eff;
		
		throw new NoSuchElementException("SpecialEffect not found for name: '" + name + "'.");
	}
}
