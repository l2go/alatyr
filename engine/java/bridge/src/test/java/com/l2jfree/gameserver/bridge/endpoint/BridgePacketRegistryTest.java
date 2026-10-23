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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ShowBoard;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.TeleportToLocation;

class BridgePacketRegistryTest
{
	private static final class NewStopMove extends StopMove
	{
		NewStopMove() { super(1, 2, 3, 4, 5); }
	}

	@Test
	void aNewSubclassIsFatalEvenWhenItInheritsADescriber()
	{
		assertThat(BridgePacketRegistry.classify(StopMove.class)).isEqualTo(BridgePacketRegistry.Kind.TRANSLATED);
		assertThat(BridgePacketRegistry.classify(TeleportToLocation.class)).isEqualTo(BridgePacketRegistry.Kind.TRANSLATED);
		assertThat(BridgePacketRegistry.classify(NewStopMove.class)).isEqualTo(BridgePacketRegistry.Kind.FATAL);
	}

	@Test
	void unsupportedPanelsAreNotMistakenForNonessentialPackets()
	{
		assertThat(BridgePacketRegistry.classify(ActionFailed.class)).isEqualTo(BridgePacketRegistry.Kind.NONESSENTIAL);
		assertThat(BridgePacketRegistry.classify(ShowBoard.class)).isEqualTo(BridgePacketRegistry.Kind.FATAL);
		assertThat(BridgePacketRegistry.classify(Object.class)).isEqualTo(BridgePacketRegistry.Kind.FATAL);
	}
}
