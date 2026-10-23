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
package com.l2jfree.gameserver.network.bridge;

import java.util.List;

/** One synchronously copied observation. Session sequence, time and epoch are assigned by its sink. */
public record BridgeFact(int kind, List<BridgeField> fields)
{
	public BridgeFact
	{
		if (kind < 1 || kind > 24)
			throw new IllegalArgumentException("fact kind");
		fields = List.copyOf(fields);
		BridgeField.requireUnique(fields);
		for (BridgeField field : fields)
			if (field.number() <= 6 || field.number() == 10 || field.number() == 16
					|| field.number() == 21 || field.number() == 32)
				throw new IllegalArgumentException("non-game fact field");
	}

	public static BridgeFact of(int kind, BridgeField... fields)
	{
		return new BridgeFact(kind, List.of(fields));
	}
}
