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

/**
 * A boss whose NPC template is not in this world is absent. Any other failure of the spawn setup stays a warning.
 */
final class BossInit
{
	private static boolean _reported;
	
	private BossInit()
	{
	}
	
	static void failed(Logger log, Exception e)
	{
		if (e instanceof NullPointerException)
		{
			if (!_reported)
			{
				_reported = true;
				log.info("World has no boss templates");
			}
		}
		else
			log.warn(e.getMessage(), e);
	}
}
