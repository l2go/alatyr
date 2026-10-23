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

import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Value;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.client.Action;
import com.l2jfree.gameserver.network.packets.client.AttackRequest;
import com.l2jfree.gameserver.network.packets.client.MoveBackwardToLocation;
import com.l2jfree.gameserver.network.packets.client.RequestAquireSkill;
import com.l2jfree.gameserver.network.packets.client.RequestBuyItem;
import com.l2jfree.gameserver.network.packets.client.RequestBypassToServer;
import com.l2jfree.gameserver.network.packets.client.RequestMagicSkillUse;
import com.l2jfree.gameserver.network.packets.client.RequestRestartPoint;
import com.l2jfree.gameserver.network.packets.client.RequestSellItem;

/** Validates the known payload before creating a direct packet. */
final class BridgeCommand
{
	private BridgeCommand()
	{
	}

	static L2ClientPacket packet(L2Client client, Envelope intent)
	{
		int verb = Math.toIntExact(intent.field(2).value().u());
		int[] payload = switch (verb)
		{
			case 1 -> new int[] { 11 };
			case 2, 3, 4, 5, 10 -> new int[] { 64 };
			case 6 -> new int[] { 64, 68 };
			case 7 -> new int[] { 14, 16, 17, 66 };
			case 8 -> new int[] { 14, 16, 17, 64, 66 };
			case 9 -> new int[0];
			case 12 -> new int[] { 18, 19, 64 };
			case 13 -> new int[] { 14, 18 };
			default -> throw new IllegalArgumentException("verb");
		};
		for (Field field : intent.fields())
		{
			if (field.number() <= 4)
				continue;
			boolean allowed = false;
			for (int number : payload)
				allowed |= field.number() == number;
			if (!allowed)
				throw new IllegalArgumentException("payload field");
		}
		return switch (verb)
		{
			case 2, 10 -> {
				int object = positive(intent, 64);
				yield new L2ClientPacket()
				{
					@Override
					protected void readImpl() { throw new IllegalStateException("direct packet"); }
					@Override
					protected void runImpl()
					{
						com.l2jfree.gameserver.network.bridge.TargetSelection.select(getActiveChar(), object);
					}
					@Override
					public String getType() { return "Bridge target selection"; }
				};
			}
			case 1 -> {
				Value at = required(intent, 11);
				yield MoveBackwardToLocation.forBridge(client.getActiveChar(), at.x(), at.y(), at.z());
			}
			case 3 -> AttackRequest.forBridge(positive(intent, 64));
			case 4, 5 -> Action.forBridge(positive(intent, 64));
			case 6 -> {
				int npc = positive(intent, 64);
				if (client.getActiveChar().getTarget() == null || client.getActiveChar().getTargetId() != npc)
					throw new IllegalArgumentException("dialogue target");
				yield RequestBypassToServer.forBridge(new String(required(intent, 68).data(), java.nio.charset.StandardCharsets.UTF_8));
			}
			case 7 -> RequestBuyItem.forBridge(integer(intent, 14), positive(intent, 66), positiveCount(intent));
			case 8 -> {
				int object = positive(intent, 64);
				int item = positive(intent, 66);
				var owned = client.getActiveChar().getInventory().getItemByObjectId(object);
				if (owned == null || owned.getItemId() != item)
					throw new IllegalArgumentException("owned item");
				yield RequestSellItem.forBridge(integer(intent, 14), object, item, positiveCount(intent));
			}
			case 9 -> RequestRestartPoint.forBridge();
			case 12, 13 -> {
				if (verb == 12)
					positive(intent, 64);
				Value skill = required(intent, 18);
				int id = skillNumber(skill, 1);
				int level = skillNumber(skill, 2);
				if (verb == 13)
					yield RequestAquireSkill.forBridge(id, level, integer(intent, 14));
				if (client.getActiveChar().getSkillLevel(id) != level)
					throw new IllegalArgumentException("skill level");
				Field force = intent.field(19);
				yield RequestMagicSkillUse.forBridge(id, force != null && force.value().u() != 0);
			}
			default -> throw new IllegalArgumentException("verb");
		};
	}

	static Value required(Envelope intent, int field)
	{
		Field value = intent.field(field);
		if (value == null)
			throw new IllegalArgumentException("payload");
		return value.value();
	}

	static int integer(Envelope intent, int field)
	{
		return Math.toIntExact(required(intent, field).s());
	}

	static int positive(Envelope intent, int field)
	{
		int value = integer(intent, field);
		if (value <= 0)
			throw new IllegalArgumentException("identifier");
		return value;
	}

	private static long positiveCount(Envelope intent)
	{
		long count = required(intent, 16).s();
		if (count <= 0)
			throw new IllegalArgumentException("count");
		return count;
	}

	private static int skillNumber(Value skill, int number)
	{
		for (Field field : skill.fields())
			if (field.number() == number)
			{
				int value = Math.toIntExact(field.value().s());
				if (value <= 0)
					throw new IllegalArgumentException("skill");
				return value;
			}
		throw new IllegalArgumentException("skill");
	}
}
