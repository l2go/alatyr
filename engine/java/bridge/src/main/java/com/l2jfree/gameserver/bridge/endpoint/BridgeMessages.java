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
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeMessageArg;

/** Presentation is neutral; only exact typed award IDs produce reward observations. */
final class BridgeMessages
{
	private BridgeMessages() {}

	static List<BridgeFact> describe(int id, List<BridgeMessageArg> args, boolean own, int actor)
	{
		var facts = new ArrayList<BridgeFact>();
		boolean literal = id == SystemMessageId.S1.getId() && args.size() == 1 && args.get(0).type() == 0;
		String text = literal ? args.get(0).text() : fallback(id, args);
		facts.add(BridgeFact.of(18, BridgeField.text(20, text, literal ? 4 : 2),
				BridgeField.string(26, "world-message"), BridgeField.number(71, id),
				BridgeField.list(72, args.stream().map(BridgeMessageArg::value).toList())));
		if (id == 45 || id == 331 || id == 95)
		{
			int expected = id == 95 ? 2 : 1;
			if (args.size() != expected || args.get(0).type() != (id == 331 ? 1 : 6)
					|| (id == 95 && args.get(1).type() != 1))
				throw new IllegalArgumentException("invalid typed award");
			var stats = new ArrayList<BridgeField>();
			if (id != 331)
				stats.add(BridgeField.number(6, args.get(0).number()));
			if (id != 45)
				stats.add(BridgeField.number(7, args.get(id == 95 ? 1 : 0).number()));
			if (own)
				facts.add(BridgeFact.of(17, BridgeField.number(64, actor),
						BridgeField.object(25, stats.toArray(BridgeField[]::new)), BridgeField.flag(19, true)));
		}
		return List.copyOf(facts);
	}

	private static String fallback(int id, List<BridgeMessageArg> args)
	{
		var text = new StringBuilder("World message ").append(id);
		if (!args.isEmpty())
		{
			text.append(" (");
			for (int i = 0; i < args.size(); i++)
			{
				if (i != 0)
					text.append(", ");
				BridgeMessageArg arg = args.get(i);
				switch (arg.type())
				{
					case 0 -> text.append(arg.text());
					case 1, 6 -> text.append(arg.number());
					case 2 -> text.append("npc ").append(arg.number());
					case 3 -> text.append("item ").append(arg.number());
					case 4 -> text.append("skill ").append(arg.number()).append(" level ").append(arg.second());
					case 5 -> text.append("fort ").append(arg.number());
					case 7 -> text.append("zone ").append(arg.number()).append(", ").append(arg.second())
							.append(", ").append(arg.third());
					default -> throw new IllegalArgumentException("unknown message argument");
				}
			}
			text.append(')');
		}
		return text.toString();
	}
}
