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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.network.bridge.BridgeValue;
import com.l2jfree.gameserver.network.packets.server.Attack;
import com.l2jfree.gameserver.network.packets.server.MyTargetSelected;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.TargetUnselected;
import com.l2jfree.gameserver.network.packets.server.TeleportToLocation;

class BridgeMovementDescribeTest
{
	@Test
	void aForeignTeleportOnlyCorrectsPositionWithoutOwnGenerationOrCompletion()
	{
		var other = mock(L2Object.class);
		when(other.getObjectId()).thenReturn(70);
		var self = BridgeTestPlayers.player();
		when(self.getObjectId()).thenReturn(10);
		var sink = new BridgeTestFacts();
		new TeleportToLocation(other, -1, 2, 3, 4).describe(sink, self);
		assertThat(sink.facts).hasSize(1);
		var fact = sink.facts.get(0);
		assertThat(fact.kind()).isEqualTo(4);
		assertThat(BridgeTestFacts.field(fact, 64).value()).isEqualTo(new BridgeValue.Number(70));
		assertThat(BridgeTestFacts.field(fact, 13).value()).isEqualTo(new BridgeValue.Position(-1, 2, 3));
		assertThat(BridgeTestFacts.field(fact, 66)).isNull();
		assertThat(BridgeTestFacts.field(fact, 67)).isNull();
		org.mockito.Mockito.verify(self, org.mockito.Mockito.never()).completeBridgeTeleport(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void ownTeleportUsesOnlyTheGenerationCapturedAtTheSourceCall()
	{
		var self = BridgeTestPlayers.player();
		when(self.getObjectId()).thenReturn(10);
		var first = TeleportToLocation.forBridgeRelocation(self, 1, 2, 3, 4, 1);
		var second = TeleportToLocation.forBridgeRelocation(self, 5, 6, 7, 8, 2);
		var sink = new BridgeTestFacts();
		second.describe(sink, self);
		first.describe(sink, self);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 66).value()).isEqualTo(new BridgeValue.Number(2));
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 66).value()).isEqualTo(new BridgeValue.Number(1));
		assertThatIllegalArgumentException().isThrownBy(() -> new TeleportToLocation(self, 1, 2, 3, 0).describe(sink, self));
		assertThat(sink.facts).hasSize(2);
	}

	@Test
	void targetPacketNamesDoNotSwapTheActorAndTarget()
	{
		var self = BridgeTestPlayers.player();
		when(self.getObjectId()).thenReturn(10);
		var target = mock(L2Object.class);
		when(target.getObjectId()).thenReturn(70);
		var sink = new BridgeTestFacts();
		new MyTargetSelected(self, target).describe(sink, self);
		new TargetUnselected(self).describe(sink, self);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 64).value()).isEqualTo(new BridgeValue.Number(10));
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 65).value()).isEqualTo(new BridgeValue.Number(70));
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 64).value()).isEqualTo(new BridgeValue.Number(10));
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 65).value()).isEqualTo(new BridgeValue.Number(0));
	}

	@Test
	void movementSnapshotsAndIndividualHitsDescribeOnlyTransmittedFields()
	{
		var sink = new BridgeTestFacts();
		new StopMove(10, -1, 2, 3, 4).describe(sink, null);
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 13).value()).isEqualTo(new BridgeValue.Position(-1, 2, 3));
		var actor = mock(L2Creature.class);
		var target = mock(L2Object.class);
		when(actor.getObjectId()).thenReturn(10);
		when(target.getObjectId()).thenReturn(70);
		var attack = new Attack(actor, target, false, 0);
		attack.hit(attack.createHit(target, 9, true, true, (byte)0));
		attack.describe(sink, null);
		assertThat(sink.facts.get(1).kind()).isEqualTo(5);
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 18).value()).isEqualTo(new BridgeValue.Flag(false));
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 17).value()).isEqualTo(new BridgeValue.Number(9));
	}
}
