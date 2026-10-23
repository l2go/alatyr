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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A numbered semantic field. Encoding and essentiality belong to the bridge, not the game. */
public record BridgeField(int number, BridgeValue value)
{
	public BridgeField
	{
		if (number <= 0)
			throw new IllegalArgumentException("field number");
		Objects.requireNonNull(value);
	}

	public static BridgeField number(int number, long value)
	{
		return new BridgeField(number, new BridgeValue.Number(value));
	}

	public static BridgeField flag(int number, boolean value)
	{
		return new BridgeField(number, new BridgeValue.Flag(value));
	}

	public static BridgeField string(int number, String value)
	{
		return new BridgeField(number, new BridgeValue.StringValue(value));
	}

	public static BridgeField at(int number, int x, int y, int z)
	{
		return new BridgeField(number, new BridgeValue.Position(x, y, z));
	}

	public static BridgeField object(int number, BridgeField... fields)
	{
		return new BridgeField(number, new BridgeValue.ObjectValue(List.of(fields)));
	}

	public static BridgeField list(int number, List<? extends BridgeValue> items)
	{
		return new BridgeField(number, new BridgeValue.ListValue(List.copyOf(items)));
	}

	public static BridgeField text(int number, String value, int origin)
	{
		if (origin < 1 || origin > 4)
			throw new IllegalArgumentException("text origin");
		return object(number, string(1, value), number(2, origin));
	}

	static void requireUnique(List<BridgeField> fields)
	{
		var numbers = new HashSet<Integer>();
		for (BridgeField field : fields)
			if (!numbers.add(field.number()))
				throw new IllegalArgumentException("duplicate semantic field");
	}
}
