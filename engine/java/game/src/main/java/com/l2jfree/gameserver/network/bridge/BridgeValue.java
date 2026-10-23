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

/** Immutable receiver-visible values; no network buffer or game object crosses the SPI. */
public sealed interface BridgeValue
{
	record Number(long value) implements BridgeValue {}
	record Flag(boolean value) implements BridgeValue {}
	record StringValue(String value) implements BridgeValue
	{
		public StringValue { Objects.requireNonNull(value); }
	}
	record Position(int x, int y, int z) implements BridgeValue {}
	record ObjectValue(List<BridgeField> fields) implements BridgeValue
	{
		public ObjectValue
		{
			fields = List.copyOf(fields);
			BridgeField.requireUnique(fields);
		}
	}
	record ListValue(List<BridgeValue> items) implements BridgeValue
	{
		public ListValue { items = List.copyOf(items); }
	}
}
