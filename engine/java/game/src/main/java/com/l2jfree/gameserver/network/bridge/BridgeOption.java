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

import java.util.Objects;

/** A copied dialogue action; it is never a participant command or a diagnostic string. */
public record BridgeOption(int option, String label, String savedAction)
{
	public BridgeOption
	{
		if (option <= 0)
			throw new IllegalArgumentException("option");
		Objects.requireNonNull(label);
		Objects.requireNonNull(savedAction);
		if (savedAction.isEmpty() || savedAction.indexOf('\0') >= 0)
			throw new IllegalArgumentException("saved action");
	}

	public BridgeValue value()
	{
		return BridgeField.object(1, BridgeField.number(1, option), BridgeField.text(2, label, 1),
				BridgeField.string(3, savedAction)).value();
	}

	@Override
	public String toString()
	{
		return "BridgeOption[option=" + option + "]";
	}
}
