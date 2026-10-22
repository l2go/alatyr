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
package com.l2jfree.gameserver.network.bridge;

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 * Selects a target without acting on it. The click packet keeps its own checks and then acts; this entry only sets
 * the target. Selecting the object that is already selected does nothing.
 */
public final class TargetSelection
{
	private TargetSelection()
	{
	}
	
	public static void select(L2Player player, int objectId)
	{
		if (player == null || objectId <= 0)
			return;
		
		if (player.inObserverMode())
		{
			player.sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final L2Object obj;
		if (player.getTargetId() == objectId)
			obj = player.getTarget();
		else
			obj = L2World.getInstance().findObject(objectId);
		
		if (obj == null)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		else if (obj instanceof L2Player)
		{
			L2Player target = (L2Player)obj;
			if (target.getAppearance().isInvisible() && !player.isGM())
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		
		if (!player.isSameInstance(obj))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.getTarget() == obj)
			return;
		
		player.setTarget(obj);
	}
}
