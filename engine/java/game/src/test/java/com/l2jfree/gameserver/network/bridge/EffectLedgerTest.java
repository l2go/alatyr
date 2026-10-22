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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * The ledger records only while an attempt is open on this thread. An inactive call returns without a receipt.
 */
class EffectLedgerTest
{
	@AfterEach
	void closeLedger()
	{
		EffectLedger.close();
	}
	
	@Test
	void anInactiveLedgerReturns()
	{
		L2Player player = mock(L2Player.class);
		
		EffectLedger.record(player, BridgeEffect.coin(1));
		EffectLedger.expectLearningCost(player, 0, 1, 0, 0);
		EffectLedger.close();
		
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		assertThat(attempt.effects()).isEmpty();
		assertThat(attempt.isLedgerIncomplete()).isFalse();
		assertThat(EffectLedger.isActive()).isTrue();
	}
	
	@Test
	void aCoinIsRecordedForTheOpenPlayer()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		EffectLedger.record(player, BridgeEffect.coin(1));
		
		assertThat(attempt.effects()).containsExactly(BridgeEffect.coin(1));
		assertThat(attempt.isLedgerIncomplete()).isFalse();
	}
	
	@Test
	void anotherPlayersChangeIsIgnored()
	{
		L2Player player = mock(L2Player.class);
		L2Player other = mock(L2Player.class);
		Attempt attempt = open(player);
		
		EffectLedger.record(other, BridgeEffect.coin(1));
		
		assertThat(attempt.effects()).isEmpty();
		assertThat(attempt.isLedgerIncomplete()).isFalse();
	}
	
	@Test
	void aMissingOwnerOrEffectMarksTheLedgerIncomplete()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		EffectLedger.record(null, BridgeEffect.coin(1));
		
		assertThat(attempt.effects()).isEmpty();
		assertThat(attempt.isLedgerIncomplete()).isTrue();
	}
	
	@Test
	void theSixtyFifthEffectIsNotStored()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		for (int i = 1; i <= Attempt.MAX_EFFECTS; i++)
			EffectLedger.record(player, BridgeEffect.coin(i));
		
		assertThat(attempt.effects()).hasSize(Attempt.MAX_EFFECTS);
		assertThat(attempt.isLedgerIncomplete()).isFalse();
		
		EffectLedger.record(player, BridgeEffect.coin(1));
		
		assertThat(attempt.effects()).hasSize(Attempt.MAX_EFFECTS);
		assertThat(attempt.isLedgerIncomplete()).isTrue();
	}
	
	@Test
	void theSameLearningCostIsNotASecondReceipt()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		EffectLedger.expectLearningCost(player, 0, 10, 0, 0);
		EffectLedger.expectLearningCost(player, 0, 10, 0, 0);
		
		assertThat(attempt.effects()).isEmpty();
		assertThat(attempt.isLedgerIncomplete()).isFalse();
	}
	
	@Test
	void aDifferentLearningCostMarksTheLedgerIncomplete()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		EffectLedger.expectLearningCost(player, 0, 10, 0, 0);
		EffectLedger.expectLearningCost(player, 0, 11, 0, 0);
		
		assertThat(attempt.isLedgerIncomplete()).isTrue();
	}
	
	@Test
	void anUnsupportedBranchOrAChangedClassMarksTheLedgerIncomplete()
	{
		L2Player clan = mock(L2Player.class);
		Attempt clanAttempt = open(clan);
		EffectLedger.expectLearningCost(clan, 5, 1, 57, 1);
		assertThat(clanAttempt.isLedgerIncomplete()).isTrue();
		EffectLedger.close();
		
		L2Player changed = mock(L2Player.class);
		when(changed.getClassIndex()).thenReturn(0, 1);
		Attempt changedAttempt = new Attempt(changed);
		EffectLedger.open(changedAttempt);
		EffectLedger.expectLearningCost(changed, 0, 1, 0, 0);
		assertThat(changedAttempt.isLedgerIncomplete()).isTrue();
	}
	
	@Test
	void openingTwiceIsRejectedAndAClosedLedgerRecordsNothing()
	{
		L2Player player = mock(L2Player.class);
		Attempt attempt = open(player);
		
		assertThatThrownBy(() -> EffectLedger.open(new Attempt(player))).isInstanceOf(IllegalStateException.class);
		
		EffectLedger.close();
		EffectLedger.record(player, BridgeEffect.coin(1));
		
		assertThat(attempt.effects()).isEmpty();
		assertThat(EffectLedger.isActive()).isFalse();
	}
	
	@Test
	void illegalEffectsAreRejected()
	{
		assertThatThrownBy(() -> BridgeEffect.coin(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.item(1, 2, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.skill(1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.skill(1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.sp(0, 1, 2)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.sp(5, 3, 3)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.sp(5, -1, 4)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BridgeEffect.target(-1)).isInstanceOf(IllegalArgumentException.class);
		
		BridgeEffect removal = BridgeEffect.skill(5, 3, 0);
		assertThat(removal.kind()).isEqualTo(BridgeEffect.SKILL);
		assertThat(removal.oldValue()).isEqualTo(3);
		assertThat(removal.newValue()).isEqualTo(0);
		
		BridgeEffect points = BridgeEffect.sp(-40, 100, 60);
		assertThat(points.kind()).isEqualTo(BridgeEffect.SP);
		assertThat(points.delta()).isEqualTo(-40);
		assertThat(points.oldValue()).isEqualTo(100);
		assertThat(points.newValue()).isEqualTo(60);
		assertThat(BridgeEffect.target(0).target()).isEqualTo(0);
	}
	
	private static Attempt open(L2Player player)
	{
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		return attempt;
	}
}
