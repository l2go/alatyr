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
package com.l2jfree.gameserver.instancemanager.grandbosses;

import org.slf4j.Logger;

import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;

/**
 * A boss whose NPC template is not in this world is left out before any spawn is prepared.
 * A failure while preparing a boss that does have a template stays a warning at the call site.
 */
final class BossInit
{
	private BossInit()
	{
	}
	
	/**
	 * @return {@code true} when this world has no such template and the caller should leave the boss out
	 */
	static boolean missing(Logger log, String boss, L2NpcTemplate template)
	{
		if (template != null)
			return false;
		log.info("World has no " + boss + " template");
		return true;
	}
}
