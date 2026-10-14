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
package com.l2jfree.gameserver.model.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.rewards.HuntRewards.Drop;
import com.l2jfree.gameserver.model.rewards.HuntRewards.Member;
import com.l2jfree.gameserver.model.rewards.HuntRewards.PlayerReward;
import com.l2jfree.gameserver.model.rewards.HuntRewards.Result;
import com.l2jfree.gameserver.model.rewards.HuntRewards.Side;
import com.l2jfree.gameserver.model.rewards.HuntRewards.Snapshot;

class HuntRewardsTest
{
	private static final Drop BOTTLE = new Drop(60001, 1);
	private static final Drop SWORD = new Drop(60002, 1);

	private static Side solo(long id, double damage)
	{
		return new Side(id, damage, 10, false, List.of(new Member(id, 10)), 0);
	}

	private static Snapshot snapshot(long lifeId, Side... sides)
	{
		return new Snapshot("test-world", lifeId, 10, 88, 8, 0, 0, List.of(sides));
	}

	private static Result reward(long lifeId, long coins, Side... sides)
	{
		return HuntRewards.calculate(snapshot(lifeId, sides), List.of(new Drop(57, coins), BOTTLE));
	}

	private static PlayerReward player(Result result, long id)
	{
		return result.sides().stream().flatMap(side -> side.players().stream()).filter(p -> p.playerId() == id).findFirst().orElseThrow();
	}

	@Test
	void soloReceivesFullReward()
	{
		Result result = reward(1, 12, solo(1, 1000));
		assertThat(player(result, 1)).isEqualTo(new PlayerReward(1, 88, 8, 12));
		assertThat(result.items()).hasSize(1);
		assertThat(result.items().getFirst().sideId()).isEqualTo(1);
	}

	@Test
	void seventyFivePercentGetsTheItemAndProportionalProgress()
	{
		Result result = reward(1, 12, solo(1, 750), solo(2, 250));
		assertThat(player(result, 1)).isEqualTo(new PlayerReward(1, 66, 6, 9));
		assertThat(player(result, 2)).isEqualTo(new PlayerReward(2, 22, 2, 3));
		assertThat(result.items().getFirst().sideId()).isEqualTo(1);
	}

	@Test
	void tenPercentBoundaryIsIncluded()
	{
		Result result = reward(1, 12, solo(1, 900), solo(2, 100));
		assertThat(player(result, 1).coins()).isEqualTo(11);
		assertThat(player(result, 2).coins()).isEqualTo(1);
		assertThat(result.sides()).allMatch(HuntRewards.SideReward::admitted);
		assertThat(result.items().getFirst().sideId()).isEqualTo(1);
	}

	@Test
	void fiftyFiveFortyFiveUsesLargestRemaindersAndWeightedDraws()
	{
		int first = 0;
		for (long seed = 1; seed <= 10000; seed++)
		{
			Result result = reward(seed, 12, solo(1, 550), solo(2, 450));
			assertThat(player(result, 1).coins()).isEqualTo(7);
			assertThat(player(result, 2).coins()).isEqualTo(5);
			if (result.items().getFirst().sideId() == 1)
				first++;
		}
		assertThat(first).isBetween(5300, 5700);
	}

	@Test
	void fourCoinsAreSplitBetweenThreeSides()
	{
		Result result = reward(1, 4, solo(1, 400), solo(2, 350), solo(3, 250));
		assertThat(player(result, 1).coins()).isEqualTo(2);
		assertThat(player(result, 2).coins()).isEqualTo(1);
		assertThat(player(result, 3).coins()).isEqualTo(1);
	}

	@Test
	void excludingNinePercentDoesNotCreateDominance()
	{
		Set<Long> winners = new HashSet<>();
		for (long seed = 1; seed <= 200; seed++)
		{
			Result result = reward(seed, 12, solo(1, 550), solo(2, 360), solo(3, 90));
			assertThat(player(result, 3).coins()).isZero();
			assertThat(player(result, 3).xp()).isEqualTo(7);
			winners.add(result.items().getFirst().sideId());
		}
		assertThat(winners).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void separateItemsHaveSeparateDrawsAndThirdSideCanWin()
	{
		boolean differentWinners = false;
		boolean thirdWon = false;
		for (long seed = 1; seed <= 200; seed++)
		{
			Result result = HuntRewards.calculate(snapshot(seed, solo(1, 400), solo(2, 390), solo(3, 210)), List.of(BOTTLE, SWORD));
			assertThat(result.items()).hasSize(2);
			differentWinners |= result.items().get(0).sideId() != result.items().get(1).sideId();
			thirdWon |= result.items().stream().anyMatch(item -> item.sideId() == 3);
		}
		assertThat(differentWinners).isTrue();
		assertThat(thirdWon).isTrue();
	}

	@Test
	void groupFortyPercentIsAppliedOnce()
	{
		Side group = new Side(-1, 400, 10, false, List.of(new Member(1, 10), new Member(2, 10)), 0);
		Snapshot snapshot = new Snapshot("test-world", 1, 10, 1000, 100, 0, 0, List.of(group, solo(3, 600)));
		Result result = HuntRewards.calculate(snapshot, List.of(new Drop(57, 20), BOTTLE));
		assertThat(player(result, 1)).isEqualTo(new PlayerReward(1, 200, 20, 4));
		assertThat(player(result, 2)).isEqualTo(new PlayerReward(2, 200, 20, 4));
		assertThat(player(result, 3)).isEqualTo(new PlayerReward(3, 600, 60, 12));
		assertThat(result.items().getFirst().sideId()).isEqualTo(3);
	}

	@Test
	void healerSharesTheGroupsRewardWithoutDoingDamage()
	{
		Side group = new Side(-1, 1000, 10, false, List.of(new Member(1, 10), new Member(2, 10)), 0);
		Result result = reward(1, 12, group);
		assertThat(player(result, 1)).isEqualTo(new PlayerReward(1, 44, 4, 6));
		assertThat(player(result, 2)).isEqualTo(new PlayerReward(2, 44, 4, 6));
	}

	@Test
	void partyRemainderRotatesBetweenKills()
	{
		Side group = new Side(-1, 1000, 10, false, List.of(new Member(1, 10), new Member(2, 10)), 0);
		Result first = reward(1, 13, group);
		int cursor = first.sides().getFirst().nextCoinCursor();
		Result second = reward(2, 13, new Side(-1, 1000, 10, false, group.members(), cursor));
		assertThat(player(first, 1).coins()).isEqualTo(7);
		assertThat(player(first, 2).coins()).isEqualTo(6);
		assertThat(player(second, 1).coins()).isEqualTo(6);
		assertThat(player(second, 2).coins()).isEqualTo(7);
	}

	@Test
	void guardsShareIsNotGivenToTheTaggingPlayer()
	{
		Side guard = new Side(0, 999, 10, true, List.of(), 0);
		Result result = reward(1, 12, guard, solo(1, 1));
		assertThat(player(result, 1)).isEqualTo(new PlayerReward(1, 0, 0, 0));
		assertThat(result.items().getFirst().sideId()).isZero();
		assertThat(result.sides().stream().mapToLong(HuntRewards.SideReward::coins).sum()).isEqualTo(12);
	}

	@Test
	void levelPenaltyPreventsTheStrongPlayerFromTakingTheItem()
	{
		Side strong = new Side(1, 700, 25, false, List.of(new Member(1, 25)), 0);
		Result result = reward(1, 12, strong, solo(2, 300));
		assertThat(HuntRewards.lootRate(25, 10)).isBetween(0.099, 0.100);
		assertThat(result.items().getFirst().sideId()).isEqualTo(2);
		assertThat(player(result, 1).xp()).isEqualTo(9);
	}

	@Test
	void elevenEqualSidesUseTheHalfMaximumFallback()
	{
		List<Side> sides = new ArrayList<>();
		for (int i = 1; i <= 11; i++)
			sides.add(solo(i, 100));
		Result result = reward(1, 12, sides.toArray(Side[]::new));
		assertThat(result.sides()).hasSize(11).allMatch(HuntRewards.SideReward::admitted);
		assertThat(result.sides().stream().mapToLong(HuntRewards.SideReward::coins).sum()).isEqualTo(12);
		assertThat(result.sides().stream().filter(side -> side.coins() == 2).count()).isEqualTo(1);
		assertThat(result.sides()).allMatch(side -> side.coins() == 1 || side.coins() == 2);
		Set<Long> winners = new HashSet<>();
		Set<Long> extraCoinWinners = new HashSet<>();
		for (long seed = 1; seed <= 200; seed++)
		{
			Result next = reward(seed, 12, sides.toArray(Side[]::new));
			winners.add(next.items().getFirst().sideId());
			next.sides().stream().filter(side -> side.coins() == 2).forEach(side -> extraCoinWinners.add(side.sideId()));
		}
		assertThat(winners).hasSize(11);
		assertThat(extraCoinWinners).hasSize(11);
	}

	@Test
	void fallbackStillExcludesATinyTag()
	{
		List<Side> sides = new ArrayList<>();
		for (int i = 1; i <= 11; i++)
			sides.add(solo(i, 100));
		sides.add(solo(12, 1));
		for (long seed = 1; seed <= 100; seed++)
		{
			Result result = reward(seed, 12, sides.toArray(Side[]::new));
			assertThat(result.sides().stream().filter(HuntRewards.SideReward::admitted).count()).isEqualTo(11);
			assertThat(player(result, 12).coins()).isZero();
			assertThat(result.items().getFirst().sideId()).isNotEqualTo(12);
		}
	}

	@Test
	void halfMaximumBoundaryIsIncludedAndAppliesToNeutralSides()
	{
		List<Side> sides = new ArrayList<>();
		for (int i = 1; i <= 11; i++)
			sides.add(solo(i, 100));
		sides.add(solo(12, 50));
		sides.add(new Side(0, 49, 10, true, List.of(), 0));
		Result result = reward(1, 120, sides.toArray(Side[]::new));
		assertThat(result.sides().stream().filter(side -> side.sideId() == 12).findFirst().orElseThrow().admitted()).isTrue();
		assertThat(result.sides().stream().filter(side -> side.sideId() == 0).findFirst().orElseThrow().admitted()).isFalse();
	}

	@Test
	void deadGroupWithDominanceLeavesTheItemUnclaimed()
	{
		Side deadGroup = new Side(-1, 600, 10, false, List.of(), 0);
		Snapshot snapshot = new Snapshot("test-world", 1, 10, 1000, 100, 0, 0, List.of(deadGroup, solo(3, 400)));
		Result result = HuntRewards.calculate(snapshot, List.of(new Drop(57, 20), BOTTLE));
		assertThat(result.items().getFirst().sideId()).isEqualTo(-1);
		assertThat(result.items().getFirst().unclaimed()).isTrue();
		assertThat(result.unclaimedCoins()).isEqualTo(12);
		assertThat(player(result, 3)).isEqualTo(new PlayerReward(3, 400, 40, 8));
	}

	@Test
	void deadSoloReceivesNoProgressCoinsOrItems()
	{
		Side dead = new Side(1, 750, 10, false, List.of(), 0);
		Result result = reward(1, 12, dead, solo(2, 250));
		assertThat(result.sides().getFirst().players()).isEmpty();
		assertThat(result.sides().getFirst().unclaimed()).isTrue();
		assertThat(result.items().getFirst().unclaimed()).isTrue();
		assertThat(result.unclaimedCoins()).isEqualTo(9);
	}

	@Test
	void nearbyLivingSoloKeepsOnlyItsOwnShare()
	{
		Side absent = new Side(1, 750, 10, false, List.of(), 0);
		Result result = reward(1, 12, absent, solo(2, 250));
		assertThat(player(result, 2)).isEqualTo(new PlayerReward(2, 22, 2, 3));
		assertThat(result.items()).allMatch(HuntRewards.ItemReward::unclaimed);
		long delivered = result.sides().stream().flatMap(side -> side.players().stream()).mapToLong(PlayerReward::coins).sum();
		assertThat(delivered).isEqualTo(12 - result.unclaimedCoins());
	}

	@Test
	void sameSnapshotIsIndependentOfCollectionOrder()
	{
		List<Side> sides = new ArrayList<>(List.of(solo(1, 400), solo(2, 390), solo(3, 210)));
		Snapshot first = new Snapshot("test-world", 33, 10, 88, 8, 0, 0, sides);
		Collections.reverse(sides);
		Snapshot second = new Snapshot("test-world", 33, 10, 88, 8, 0, 0, sides);
		assertThat(HuntRewards.calculate(first, List.of(new Drop(57, 13), BOTTLE, SWORD)))
				.isEqualTo(HuntRewards.calculate(second, List.of(new Drop(57, 13), BOTTLE, SWORD)));
	}

	@Test
	void integerRewardIsNotLostToAnIntermediateRecurringFraction()
	{
		Snapshot snapshot = new Snapshot("test-world", 1, 10, 3, 3, 0, 0, List.of(solo(1, 1), solo(2, 2)));
		Result result = HuntRewards.calculate(snapshot, List.of());
		assertThat(player(result, 1).xp()).isEqualTo(1);
		assertThat(player(result, 1).sp()).isEqualTo(1);
	}

	@Test
	void fewerCoinsThanSidesAreStillConserved()
	{
		for (int coins = 1; coins <= 30; coins++)
		{
			Result result = reward(coins, coins, solo(1, 400), solo(2, 350), solo(3, 250));
			assertThat(result.sides().stream().mapToLong(HuntRewards.SideReward::coins).sum()).isEqualTo(coins);
		}
	}
}
