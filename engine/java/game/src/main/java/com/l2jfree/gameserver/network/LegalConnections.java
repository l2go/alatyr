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
package com.l2jfree.gameserver.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The addresses that the login server announced to this world and that may connect once for each announcement. The
 * announcement comes from the login link and the connection from a network thread, so the counts are changed from
 * several threads.
 */
final class LegalConnections
{
	private final Map<String, Integer> _counts = new ConcurrentHashMap<String, Integer>();
	
	/** Announces one more connection from the address. */
	void legalize(String ip)
	{
		_counts.merge(ip, 1, Integer::sum);
	}
	
	/** @return true if the address was announced, and then takes one announcement from it */
	boolean consume(String ip)
	{
		final boolean[] announced = new boolean[1];
		_counts.computeIfPresent(ip, (key, count) -> {
			announced[0] = true;
			return count == 1 ? null : count - 1;
		});
		return announced[0];
	}
	
	/** @return how many connections of the address are announced and not yet made */
	int pending(String ip)
	{
		final Integer count = _counts.get(ip);
		return count == null ? 0 : count;
	}
}
