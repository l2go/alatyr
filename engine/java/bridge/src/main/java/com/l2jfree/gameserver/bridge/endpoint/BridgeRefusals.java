/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.List;

/** Closed mapping of delivered system message IDs to verb-specific world refusals. */
final class BridgeRefusals
{
	private BridgeRefusals()
	{
	}

	/** Zero means that no message in delivery order is registered for this verb. */
	static int firstReason(int verb, List<Integer> delivered)
	{
		for (int id : delivered)
		{
			int reason = reason(verb, id);
			if (reason != 0)
				return reason;
		}
		return 0;
	}

	static int reason(int verb, int id)
	{
		return switch (id)
		{
			case 781 -> verb == 4 || verb == 5 || verb == 6 ? 16 : 0;
			case 2193 -> verb == 7 || verb == 8 || verb == 13 ? 3 : 0;
			case 1036 -> verb == 7 || verb == 8 ? 6 : 0;
			case 422 -> verb == 7 ? 2 : 0;
			case 129 -> verb == 7 || verb == 4 ? 2 : 0;
			case 350 -> verb == 7 ? 17 : 0;
			case 279 -> verb == 7 ? 1 : 0;
			case 794 -> verb == 7 ? 16 : 0;
			case 483 -> verb == 8 ? 18 : 0;
			case 276 -> verb == 13 ? 15 : 0;
			case 278 -> verb == 13 ? 14 : 0;
			case 236, 1852 -> verb == 13 ? 16 : 0;
			case 149 -> verb == 4 ? 16 : 0;
			case 55, 56, 57 -> verb == 4 ? 15 : 0;
			default -> 0;
		};
	}
}
