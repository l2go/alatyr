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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Where a client's outgoing packets and disconnect notices go.
 * <p>
 * The default sink writes the packet on the connection. A bridge sink must copy the fields it keeps and must not run
 * world work on the calling thread. {@link #ownTeleported(long)} is called while the teleport lock is held: queue
 * {@code L2Player.completeBridgeTeleport(generation)} on the client, and do not call it inline.
 */
public interface ClientSink
{
	boolean isBridge();
	
	void deliver(L2ServerPacket packet, L2Player player);
	
	void disconnect(BridgeFailure failure);
	
	void ownTeleported(long generation);
}
