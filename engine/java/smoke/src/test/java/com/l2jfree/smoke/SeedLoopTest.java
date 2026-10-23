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
package com.l2jfree.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

/** The seed-loop decisions that do not need a running server. */
class SeedLoopTest
{
	@Test
	void firstKillUsesTheAuthoredReward() throws SmokeException
	{
		SeedLoop.checkFirstReward(88, 8);
	}

	@Test
	void firstKillRejectsAHiddenMultiplier()
	{
		assertThatThrownBy(() -> SeedLoop.checkFirstReward(264, 24)).isInstanceOf(SmokeException.class)
				.hasMessageContaining("expected 88 XP and 8 SP");
	}

	@Test
	void aLaterStopKeepsItsPlaceInsteadOfTheOldDestination() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.apply(view, stopAt(7, 40, 5, -3104));
		SeedLoop.adoptDestination(view);

		assertThat(view.x).isEqualTo(40);
		assertThat(view.y).isEqualTo(5);
		assertThat(view.z).isEqualTo(-3104);
	}

	@Test
	void aLaterValidateLocationKeepsItsPlace() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.apply(view, validateAt(7, 40, 5, -3100));
		SeedLoop.adoptDestination(view);

		assertThat(view.x).isEqualTo(40);
		assertThat(view.y).isEqualTo(5);
		assertThat(view.z).isEqualTo(-3100);
	}

	@Test
	void anOlderDestinationDoesNotConfirmTheNewRequest() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.armMove(view, 250, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 40, 0, -3104));

		assertThat(view.acceptedMove).isFalse();
		assertThat(SeedLoop.unansweredMove(view, 40, 0, -3104, 250, 0, -3104).getMessage())
				.contains("neither MoveToLocation nor ActionFailed arrived");

		SeedLoop.apply(view, moveTo(7, 250, 0, -3104, 40, 0, -3104));
		assertThat(view.acceptedMove).isTrue();
		SeedLoop.adoptDestination(view);
		assertThat(view.x).isEqualTo(250);
	}

	@Test
	void actionFailedBesideTheConfirmationIsNotARefusal() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, new byte[] { 0x1f });
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));

		assertThat(view.acceptedMove).isTrue();
		assertThat(view.moveRejected).isTrue();
		SeedLoop.adoptDestination(view);
		assertThat(view.x).isEqualTo(100);
	}

	@Test
	void aSilentMoveNamesThePlacesAndActionFailedWhenItArrived() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);

		assertThat(SeedLoop.unansweredMove(view, 1, 2, 3, 100, 0, -3104).getMessage())
				.contains("neither MoveToLocation nor ActionFailed arrived")
				.contains("from 1,2,3")
				.contains("to 100,0,-3104");

		SeedLoop.apply(view, new byte[] { 0x1f });
		assertThat(SeedLoop.unansweredMove(view, 1, 2, 3, 100, 0, -3104).getMessage())
				.contains("ActionFailed arrived");
	}

	@Test
	void aBeastSeenOnBothSidesOfTheBoundaryHasOneOwner()
	{
		// Session 5 hunts spawn 29 (-69637, 258063). Session 6 hunts spawn 30 (-69637, 258303).
		// y=258183 is the midpoint. Each view stores the same object on its own side of that line.
		final int objectId = 4242;
		final int x = -69637;
		SeedLoop.View south = hunter(5, x, 258063);
		SeedLoop.View north = hunter(6, x, 258303);
		SeedLoop.placeBeast(south, objectId, x, 258182, -3104, "NpcInfo");
		SeedLoop.placeBeast(north, objectId, x, 258184, -3104, "NpcInfo");
		SeedLoop.FirstTargets targets = new SeedLoop.FirstTargets();
		Reporter reporter = reporter();

		assertThat(holders(south, north, targets, reporter)).isEqualTo(1);
		assertThat(south.firstObjectId).isEqualTo(objectId);
		assertThat(north.firstObjectId).isZero();

		SeedLoop.moveBeast(south, objectId, x, 258184, -3104, "MoveToLocation");
		SeedLoop.moveBeast(north, objectId, x, 258182, -3104, "StopMove");
		assertThat(holders(south, north, targets, reporter)).isEqualTo(1);
		assertThat(text(reporter)).contains("claim-taken")
				.contains("session=6 objectId=4242 spawn=30 beast=-69637,258182,-3104 source=StopMove")
				.contains("owner session=5 objectId=4242 spawn=29 beast=-69637,258182,-3104 source=NpcInfo");

		SeedLoop.markBeastDead(south, objectId);
		targets.release(5, objectId);
		// 4242 stays south's first target after the claim is released. North still sees it alive.
		assertThat(SeedLoop.claimNearest(north, targets, reporter)).isNull();
		assertThat(north.firstObjectId).isZero();
		SeedLoop.placeBeast(north, 4243, x, 258303, -3104, "NpcInfo");
		assertThat(SeedLoop.claimNearest(north, targets, reporter)).isNotNull();
		assertThat(north.firstObjectId).isEqualTo(4243);
		SeedLoop.forgetBeast(north, 4243);
		assertThat(SeedLoop.claimNearest(north, targets, reporter)).isNotNull();
		assertThat(north.firstObjectId).isEqualTo(4243);
		assertThat(SeedLoop.claimNearest(south, targets, reporter)).isNull();

		java.io.ByteArrayOutputStream check = new java.io.ByteArrayOutputStream();
		SmokeClient.Result southResult = new SmokeClient.Result(true, java.util.List.of(), null, south.firstObjectId);
		SmokeClient.Result northResult = new SmokeClient.Result(true, java.util.List.of(), null, north.firstObjectId);
		assertThat(SmokeClient.distinctFirstTargets(new PrintStream(check), java.util.List.of(southResult, northResult)))
				.isTrue();
		assertThat(check.toString()).contains("session=0 objectId=4242").contains("session=1 objectId=4243");
	}

	@Test
	void leavingTheViewReleasesTheBeast()
	{
		SeedLoop.View owner = hunter(5, -69637, 258063);
		SeedLoop.View other = hunter(6, -69637, 258303);
		SeedLoop.placeBeast(owner, 7, -69637, 258182, -3104, "NpcInfo");
		SeedLoop.placeBeast(other, 7, -69637, 258184, -3104, "NpcInfo");
		SeedLoop.FirstTargets targets = new SeedLoop.FirstTargets();
		Reporter reporter = reporter();
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNotNull();

		SeedLoop.forgetBeast(owner, 7);
		targets.release(5, 7);
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNull();
		assertThat(other.firstObjectId).isZero();
		SeedLoop.placeBeast(other, 9, -69637, 258303, -3104, "NpcInfo");
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(other.firstObjectId).isEqualTo(9);
		SeedLoop.forgetBeast(other, 9);
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(other.firstObjectId).isEqualTo(9);
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNull();
	}

	@Test
	void givingUpTheKillReleasesTheBeast()
	{
		SeedLoop.View owner = hunter(5, -69637, 258063);
		SeedLoop.View other = hunter(6, -69637, 258303);
		SeedLoop.placeBeast(owner, 7, -69637, 258182, -3104, "NpcInfo");
		SeedLoop.placeBeast(other, 7, -69637, 258184, -3104, "ValidateLocation");
		SeedLoop.FirstTargets targets = new SeedLoop.FirstTargets();
		Reporter reporter = reporter();
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNotNull();
		SeedLoop.placeBeast(other, 9, -69637, 258303, -3104, "NpcInfo");
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(other.firstObjectId).isEqualTo(9);
		SeedLoop.forgetBeast(other, 9);

		targets.release(5, 7);
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(other.firstObjectId).isEqualTo(9);
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNull();
	}

	@Test
	void endingTheSessionReleasesEveryBeastItHeld()
	{
		SeedLoop.View owner = hunter(5, -69637, 258063);
		SeedLoop.View other = hunter(6, -69637, 258303);
		SeedLoop.placeBeast(owner, 7, -69637, 258182, -3104, "NpcInfo");
		SeedLoop.placeBeast(owner, 8, -69637, 258100, -3104, "NpcInfo");
		SeedLoop.placeBeast(other, 7, -69637, 258184, -3104, "NpcInfo");
		SeedLoop.placeBeast(other, 8, -69637, 258200, -3104, "NpcInfo");
		SeedLoop.FirstTargets targets = new SeedLoop.FirstTargets();
		Reporter reporter = reporter();
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNotNull();
		SeedLoop.forgetBeast(owner, 8);
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNotNull();

		targets.releaseSession(5);
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(other.firstObjectId).isEqualTo(7);
		SeedLoop.forgetBeast(other, 8);
		assertThat(SeedLoop.claimNearest(other, targets, reporter)).isNotNull();
		assertThat(SeedLoop.claimNearest(owner, targets, reporter)).isNull();
	}

	@Test
	void starterRowsRequireOneWornUnitEach() throws SmokeException
	{
		SeedLoop.View view = starter();
		SeedLoop.checkStarter(view);

		SeedLoop.View doubled = starter();
		SeedLoop.apply(doubled, itemList(row(10, 1, 2L, true), row(11, 23, 1L, true), row(12, 28, 1L, true),
				row(13, 39, 1L, true)));
		assertThatThrownBy(() -> SeedLoop.checkStarter(doubled)).hasMessageContaining("enter item 1 count")
				.hasMessageContaining("expected 1").hasMessageContaining("actual 2");

		SeedLoop.View empty = starter();
		SeedLoop.apply(empty, itemList(row(10, 1, 0L, true), row(11, 23, 1L, true), row(12, 28, 1L, true),
				row(13, 39, 1L, true)));
		assertThatThrownBy(() -> SeedLoop.checkStarter(empty)).hasMessageContaining("enter item 1 count")
				.hasMessageContaining("actual 0");

		SeedLoop.View unworn = starter();
		SeedLoop.apply(unworn, itemList(row(10, 1, 1L, false), row(11, 23, 1L, true), row(12, 28, 1L, true),
				row(13, 39, 1L, true)));
		assertThatThrownBy(() -> SeedLoop.checkStarter(unworn)).hasMessageContaining("enter item 1 equipped");

		SeedLoop.View extra = starter();
		SeedLoop.apply(extra, itemList(row(10, 1, 1L, true), row(11, 23, 1L, true), row(12, 28, 1L, true),
				row(13, 39, 1L, true), row(14, 99, 1L, false)));
		assertThatThrownBy(() -> SeedLoop.checkStarter(extra)).hasMessageContaining("the starter kit");

		SeedLoop.View missing = new SeedLoop.View(0);
		missing.level = 1;
		SeedLoop.apply(missing, itemList(row(11, 23, 1L, true), row(12, 28, 1L, true), row(13, 39, 1L, true)));
		assertThatThrownBy(() -> SeedLoop.checkStarter(missing)).hasMessageContaining("enter item 1")
				.hasMessageContaining("absent");
	}

	@Test
	void theRelogSnapshotKeepsEachRowAndIgnoresOrder() throws SmokeException
	{
		SeedLoop.View first = new SeedLoop.View(0);
		SeedLoop.apply(first, itemList(row(10, 1, 1L, true), row(11, 1, 1L, false)));
		SeedLoop.View second = new SeedLoop.View(0);
		SeedLoop.apply(second, itemList(row(11, 1, 1L, false), row(10, 1, 1L, true)));
		SeedLoop.checkRelog(first.snapshot(), second.snapshot());

		SeedLoop.View bothWorn = new SeedLoop.View(0);
		SeedLoop.apply(bothWorn, itemList(row(10, 1, 1L, true), row(11, 1, 1L, true)));
		assertThat(bothWorn.snapshot()).isNotEqualTo(first.snapshot());

		SeedLoop.View stacked = new SeedLoop.View(0);
		SeedLoop.apply(stacked, itemList(row(10, 1, 2L, true)));
		assertThat(stacked.snapshot()).isNotEqualTo(first.snapshot());

		first.exp = 68L;
		String before = first.snapshot();
		first.exp = 69L;
		assertThatThrownBy(() -> SeedLoop.checkRelog(before, first.snapshot())).hasMessageContaining("re-enter");
		first.exp = 68L;
		first.sp = 12;
		assertThatThrownBy(() -> SeedLoop.checkRelog(before, first.snapshot())).hasMessageContaining("re-enter");
		first.sp = 0;
		SeedLoop.apply(first, new PacketWriter().writeC(0x5f).writeD(1).writeD(1).writeD(1).writeD(257).writeC(0)
				.toByteArray());
		String skilled = first.snapshot();
		SeedLoop.apply(first, new PacketWriter().writeC(0x5f).writeD(1).writeD(1).writeD(2).writeD(257).writeC(0)
				.toByteArray());
		assertThat(first.snapshot()).isNotEqualTo(skilled);
	}

	@Test
	void purchaseDrinkAndLearnRejectTheWrongDelta() throws SmokeException
	{
		assertThatThrownBy(() -> SeedLoop.checkPurchase(10, 1, 14, 0)).hasMessageContaining("buy coins");
		assertThatThrownBy(() -> SeedLoop.checkPurchase(2, 2, 14, 0)).hasMessageContaining("buy potions");
		SeedLoop.checkPurchase(2, 1, 14, 0);

		assertThatThrownBy(() -> SeedLoop.checkDrink(1, 1)).hasMessageContaining("drink potions");
		SeedLoop.checkDrink(0, 1);

		assertThatThrownBy(() -> SeedLoop.checkLearn(60, 72, 0)).hasMessageContaining("learn skill 257");
		assertThatThrownBy(() -> SeedLoop.checkLearn(70, 72, 1)).hasMessageContaining("learn sp");
		SeedLoop.checkLearn(60, 72, 1);
	}

	@Test
	void theBeastWaitDoesNotGrowFromALaterShorterInterval()
	{
		SeedLoop.View view = new SeedLoop.View(0);
		view.noteBeastWait(System.nanoTime() - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2_000));
		long recorded = view.longestBeastWaitMillis;
		assertThat(recorded).isGreaterThanOrEqualTo(1_500L);
		view.noteBeastWait(System.nanoTime());
		assertThat(view.longestBeastWaitMillis).isEqualTo(recorded);
	}

	@Test
	void aShortPacketFailsTheSession()
	{
		SeedLoop.View view = new SeedLoop.View(0);
		assertThatThrownBy(() -> SeedLoop.apply(view, new byte[] { 0x11, 0x01 })).isInstanceOf(SmokeException.class)
				.hasMessageContaining("the packet is too short");
	}

	@Test
	void inventoryUpdatesAddModifyAndRemoveSeparateRows() throws SmokeException
	{
		SeedLoop.View view = new SeedLoop.View(0);
		SeedLoop.apply(view, itemList());
		SeedLoop.apply(view, inventory(1, row(10, 57, 4L, false), 1, row(11, 1060, 1L, false)));
		SeedLoop.apply(view, inventory(2, row(10, 57, 8L, false), 3, row(11, 1060, 0L, false)));

		assertThat(view.snapshot()).contains("57:8:0").doesNotContain("1060");
	}

	private static SeedLoop.View starter() throws SmokeException
	{
		SeedLoop.View view = new SeedLoop.View(0);
		view.level = 1;
		SeedLoop.apply(view, itemList(row(10, 1, 1L, true), row(11, 23, 1L, true), row(12, 28, 1L, true),
				row(13, 39, 1L, true)));
		return view;
	}

	private static byte[] itemList(int[]... rows)
	{
		PacketWriter writer = new PacketWriter().writeC(0x11).writeH(1).writeH(rows.length);
		for (int[] row : rows)
			writeRow(writer, row);
		return writer.toByteArray();
	}

	private static byte[] inventory(Object... changes)
	{
		int count = changes.length / 2;
		PacketWriter writer = new PacketWriter().writeC(0x21).writeH(count);
		for (int i = 0; i < changes.length; i += 2)
		{
			writer.writeH((Integer)changes[i]);
			writeRow(writer, (int[])changes[i + 1]);
		}
		return writer.toByteArray();
	}

	private static int[] row(int objectId, int itemId, long count, boolean equipped)
	{
		return new int[] { objectId, itemId, (int)count, equipped ? 1 : 0 };
	}

	private static void writeRow(PacketWriter writer, int[] row)
	{
		writer.writeH(0).writeD(row[0]).writeD(row[1]).writeD(0).writeQ(row[2]).writeH(0).writeH(0).writeH(row[3])
				.writeD(0).writeH(0).writeH(0).writeD(0).writeD(0).writeZeros(16).writeD(-1);
	}

	private static SeedLoop.View moving()
	{
		SeedLoop.View view = new SeedLoop.View(0);
		view.objectId = 7;
		return view;
	}

	private static byte[] moveTo(int objectId, int destX, int destY, int destZ, int fromX, int fromY, int fromZ)
	{
		return new PacketWriter().writeC(0x2f).writeD(objectId).writeD(destX).writeD(destY).writeD(destZ).writeD(fromX)
				.writeD(fromY).writeD(fromZ).toByteArray();
	}

	private static byte[] stopAt(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(0x47).writeD(objectId).writeD(x).writeD(y).writeD(z).writeD(0).toByteArray();
	}

	private static byte[] validateAt(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(0x79).writeD(objectId).writeD(x).writeD(y).writeD(z).writeD(0).toByteArray();
	}

	private static SeedLoop.View hunter(int session, int x, int y)
	{
		SeedLoop.View view = new SeedLoop.View(session);
		view.x = x;
		view.y = y;
		return view;
	}

	/** How many of the two views hold the beast after each has tried to claim it. */
	private static int holders(SeedLoop.View south, SeedLoop.View north, SeedLoop.FirstTargets targets, Reporter reporter)
	{
		int held = 0;
		if (SeedLoop.claimNearest(south, targets, reporter) != null)
			held++;
		if (SeedLoop.claimNearest(north, targets, reporter) != null)
			held++;
		return held;
	}

	private static Reporter reporter()
	{
		return new Reporter(new PrintStream(new ByteArrayOutputStream()));
	}

	private static String text(Reporter reporter)
	{
		return String.join("\n", reporter.lines());
	}
}
