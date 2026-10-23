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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;

/** Copies common actor fields after their owning packet has selected its receiver-visible values. */
public final class BridgeActors
{
	private BridgeActors() {}

	public static String role(L2Npc npc, L2Player receiver)
	{
		if (receiver != null && npc.getTemplate().canTeach(receiver.getClassId()))
			return "tutor";
		return npc instanceof L2MerchantInstance ? "provisioner" : "unknown";
	}

	public static long speed(int base, double multiplier)
	{
		double speed = base * multiplier;
		if (!Double.isFinite(speed) || speed < 0 || speed > Integer.MAX_VALUE)
			throw new IllegalArgumentException("unrepresentable movement speed");
		return Math.round(speed);
	}

	public static void appeared(FactSink sink, int id, String role, String name, int x, int y, int z,
			int run, int walk, double multiplier, boolean running, boolean alive, boolean mine, Integer template)
	{
		long runSpeed = speed(run, multiplier);
		long walkSpeed = speed(walk, multiplier);
		var fields = new java.util.ArrayList<BridgeField>();
		fields.add(BridgeField.number(64, id));
		fields.add(BridgeField.string(11, role));
		fields.add(BridgeField.text(12, name, 4));
		fields.add(BridgeField.at(13, x, y, z));
		fields.add(BridgeField.number(15, running ? runSpeed : walkSpeed));
		fields.add(BridgeField.flag(70, alive));
		if (mine)
			fields.add(BridgeField.flag(19, true));
		if (template != null)
			fields.add(BridgeField.number(73, template));
		sink.movementSpeeds(id, runSpeed, walkSpeed);
		sink.emit(new BridgeFact(1, fields));
	}
}
