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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.BridgeValue;

class BridgeProjectionTest
{
	@Test
	void aNinthCurrentWindowFailsInsteadOfDiscardingAnAcceptedProposal()
	{
		var projection = new BridgeProjection();
		for (int i = 1; i <= BridgeProjection.WINDOWS; i++)
			projection.observe(BridgeFact.of(12, BridgeField.number(22, i), BridgeField.list(24, List.of())), i, 0);
		assertThat(projection.capture(1).facts()).hasSize(8);
		assertThatThrownBy(() -> projection.observe(BridgeFact.of(12, BridgeField.number(22, 9),
				BridgeField.list(24, List.of())), 9, 1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void historyKeepsOnlyTheLatestRowsAndTheirOriginalTimeAndText()
	{
		var projection = new BridgeProjection();
		for (int i = 0; i <= 256; i++)
			projection.observe(BridgeFact.of(18, BridgeField.text(20, "message " + i, 4)), i + 1, i);
		var entries = ((BridgeValue.ListValue)field(kind(projection.capture(300), 19), 28).value()).items();
		assertThat(entries).hasSize(256);
		var first = (BridgeValue.ObjectValue)entries.getFirst();
		var last = (BridgeValue.ObjectValue)entries.getLast();
		assertThat(((BridgeValue.Number)first.fields().getFirst().value()).value()).isEqualTo(1);
		assertThat(((BridgeValue.Number)last.fields().getFirst().value()).value()).isEqualTo(256);
		assertThat(projection.capture(300).facts()).extracting(BridgeFact::kind).containsExactly(19);
	}

	@Test
	void unknownDomainsNeverBecomeReadyButPresentEmptyDomainsCan()
	{
		BridgeProjection projection = new BridgeProjection();
		assertThat(projection.capture(0).facts()).isEmpty();
		projection.observe(BridgeFact.of(1, BridgeField.number(64, 70), BridgeField.flag(19, true),
				BridgeField.flag(70, true), BridgeField.string(11, "player")), 1, 0);
		projection.observe(BridgeFact.of(16, BridgeField.number(64, 70),
				BridgeField.object(25, BridgeField.number(1, 10))), 2, 0);
		projection.completeObjects();
		assertThat(projection.capture(0).ready()).isFalse();
		projection.observe(BridgeFact.of(14, BridgeField.list(24, List.of()), BridgeField.flag(31, true)), 3, 0);
		projection.observe(BridgeFact.of(23, BridgeField.list(30, List.of()), BridgeField.flag(31, true)), 4, 0);
		projection.observe(BridgeFact.of(21, BridgeField.list(29, List.of()), BridgeField.flag(31, true)), 5, 0);
		assertThat(projection.capture(0).ready()).isTrue();
		BridgeFact carried = kind(projection.capture(0), 14);
		assertThat(((BridgeValue.Number)field(carried, 23).value()).value()).isZero();
		assertThat(((BridgeValue.ListValue)field(carried, 24).value()).items()).isEmpty();
	}

	@Test
	void inventoryDeltasReplaceCountsAndRemoveDoesNotSubtractTwice()
	{
		BridgeProjection projection = new BridgeProjection();
		projection.observe(BridgeFact.of(14, BridgeField.list(24, List.of(good(70, 57, 13, 0))),
				BridgeField.flag(31, true)), 1, 0);
		projection.observe(BridgeFact.of(15, BridgeField.list(24, List.of(good(70, 57, 8, 2)))), 2, 1);
		var snapshot = projection.capture(1);
		assertThat(((BridgeValue.Number)field(kind(snapshot, 14), 23).value()).value()).isEqualTo(8);
		projection.observe(BridgeFact.of(15, BridgeField.list(24, List.of(good(70, 57, 8, 3)))), 3, 2);
		projection.observe(BridgeFact.of(15, BridgeField.list(24, List.of(good(70, 57, 8, 3)))), 4, 3);
		assertThat(((BridgeValue.Number)field(kind(projection.capture(3), 14), 23).value()).value()).isZero();
		assertThat(((BridgeValue.Number)field(kind(snapshot, 14), 23).value()).value()).isEqualTo(8);
	}

	@Test
	void snapshotRetainsExplicitLifeAndOriginalHistoryWithoutLiveWitnesses()
	{
		BridgeProjection projection = new BridgeProjection();
		projection.observe(BridgeFact.of(1, BridgeField.number(64, 70), BridgeField.flag(70, true)), 1, 0);
		projection.observe(BridgeFact.of(6, BridgeField.number(64, 70)), 2, 1);
		projection.observe(BridgeFact.of(9, BridgeField.number(64, 70), BridgeField.number(65, 80)), 3, 2);
		projection.observe(BridgeFact.of(17, BridgeField.object(25, BridgeField.number(6, 10))), 4, 3);
		projection.observe(BridgeFact.of(18, BridgeField.text(20, "A world message", 4)), 5, 7);
		var snapshot = projection.capture(10);
		assertThat(snapshot.facts()).extracting(BridgeFact::kind).containsExactly(1, 19);
		assertThat(((BridgeValue.Flag)field(kind(snapshot, 1), 70).value()).value()).isFalse();
		var entry = (BridgeValue.ObjectValue)((BridgeValue.ListValue)field(kind(snapshot, 19), 28).value()).items().get(0);
		assertThat(((BridgeValue.Number)entry.fields().get(0).value()).value()).isEqualTo(7);
	}

	@Test
	void proposalsKeepTheirOriginalAgeAndNeverRenewOnCapture()
	{
		BridgeProjection projection = new BridgeProjection();
		projection.observe(BridgeFact.of(12, BridgeField.number(22, 1), BridgeField.list(24, List.of())), 1, 7);
		for (long now : new long[] { 8, 60006 })
			assertThat(((BridgeValue.Number)field(kind(projection.capture(now), 12), 74).value()).value()).isEqualTo(7);
		assertThat(projection.capture(60007).facts()).noneMatch(fact -> fact.kind() == 12);
	}

	@Test
	void ownTeleportDropsOldVisibilityAndWindowsWithoutDroppingInventory()
	{
		BridgeProjection projection = new BridgeProjection();
		projection.observe(BridgeFact.of(1, BridgeField.number(64, 70), BridgeField.flag(19, true)), 1, 0);
		projection.observe(BridgeFact.of(1, BridgeField.number(64, 80)), 2, 0);
		projection.observe(BridgeFact.of(14, BridgeField.list(24, List.of()), BridgeField.flag(31, true)), 3, 0);
		projection.observe(BridgeFact.of(12, BridgeField.number(22, 1), BridgeField.list(24, List.of())), 4, 0);
		projection.completeObjects();
		projection.observe(BridgeFact.of(4, BridgeField.number(64, 70), BridgeField.flag(67, true),
				BridgeField.number(66, 1), BridgeField.at(13, 1, 2, 3)), 5, 1);
		var snapshot = projection.capture(1);
		assertThat(snapshot.ready()).isFalse();
		assertThat(snapshot.facts()).extracting(BridgeFact::kind).containsExactly(1, 14);
		assertThat(((BridgeValue.Number)field(kind(snapshot, 1), 64).value()).value()).isEqualTo(70);
	}

	@Test
	void inventoryCapacityFailsInsteadOfTruncatingACompleteSnapshot()
	{
		BridgeProjection projection = new BridgeProjection();
		var rows = new java.util.ArrayList<BridgeValue>();
		for (int i = 1; i <= BridgeProjection.ROWS + 1; i++)
			rows.add(good(i, 1060, 1, 0));
		assertThatThrownBy(() -> projection.observe(BridgeFact.of(14, BridgeField.list(24, rows),
				BridgeField.flag(31, true)), 1, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static BridgeValue good(int object, int item, long count, int change)
	{
		var fields = new java.util.ArrayList<BridgeField>(List.of(BridgeField.number(2, object),
				BridgeField.number(3, item), BridgeField.number(4, count)));
		if (change != 0)
			fields.add(BridgeField.number(7, change));
		return new BridgeValue.ObjectValue(fields);
	}

	private static BridgeFact kind(BridgeProjection.Snapshot snapshot, int kind)
	{
		return snapshot.facts().stream().filter(fact -> fact.kind() == kind).findFirst().orElseThrow();
	}

	private static BridgeField field(BridgeFact fact, int number)
	{
		return fact.fields().stream().filter(value -> value.number() == number).findFirst().orElseThrow();
	}
}
