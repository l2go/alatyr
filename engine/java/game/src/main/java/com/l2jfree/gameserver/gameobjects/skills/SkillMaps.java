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
package com.l2jfree.gameserver.gameobjects.skills;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The map of skills of a creature. A non-player character starts with the unmodifiable map of its template and gets a
 * map of its own when its first skill is added.
 */
public final class SkillMaps
{
	private SkillMaps()
	{
	}
	
	/** @return the skills if the creature owns them, else a changeable copy of them, empty if there are none */
	public static <V> Map<Integer, V> modifiable(Map<Integer, V> skills)
	{
		if (skills instanceof ConcurrentHashMap<?, ?>)
			return skills;
		
		Map<Integer, V> copy = new ConcurrentHashMap<Integer, V>();
		if (skills != null)
			copy.putAll(skills);
		return copy;
	}
}
