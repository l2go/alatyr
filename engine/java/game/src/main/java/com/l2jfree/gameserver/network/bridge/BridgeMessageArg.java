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
import java.util.Objects;

/** Typed system-message argument, preserving integer amounts and ordered components. */
public record BridgeMessageArg(int type, String text, long number, long second, long third)
{
	public BridgeMessageArg
	{
		if (type < 0 || type > 7)
			throw new IllegalArgumentException("message argument type");
		if (type == 0)
		{
			Objects.requireNonNull(text);
			if (number != 0 || second != 0 || third != 0)
				throw new IllegalArgumentException("text argument fields");
		}
		else if (text != null || (type != 4 && type != 7 && second != 0) || (type != 7 && third != 0))
			throw new IllegalArgumentException("numeric argument fields");
	}

	public static BridgeMessageArg text(String text)
	{
		return new BridgeMessageArg(0, text, 0, 0, 0);
	}

	public static BridgeMessageArg number(int type, long number)
	{
		return new BridgeMessageArg(type, null, number, 0, 0);
	}

	public BridgeValue value()
	{
		if (type == 0)
			return new BridgeValue.ObjectValue(List.of(BridgeField.number(1, type), BridgeField.string(2, text)));
		var fields = new java.util.ArrayList<BridgeField>();
		fields.add(BridgeField.number(1, type));
		fields.add(BridgeField.number(3, number));
		if (type == 4 || type == 7)
			fields.add(BridgeField.number(4, second));
		if (type == 7)
			fields.add(BridgeField.number(5, third));
		return new BridgeValue.ObjectValue(fields);
	}
}
