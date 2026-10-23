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

import com.l2jfree.gameserver.gameobjects.L2Player;

/** Describes only fields exposed by the same packet to this receiver. */
public interface SemanticPacket
{
	/**
	 * Runs on the sender's thread under the sink monitor, which may be entered while any game lock is held.
	 * Reads only state read by this packet's writeImpl and must not acquire locks.
	 */
	void describe(FactSink sink, L2Player receiver);
}
