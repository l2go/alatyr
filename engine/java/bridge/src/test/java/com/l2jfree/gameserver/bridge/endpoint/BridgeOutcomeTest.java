package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

class BridgeOutcomeTest
{
	@Test
	void committedMultisellMarksTheOwnerLedgerIncompleteAndUnknown()
	{
		var player = BridgeTestPlayers.player();
		var inventory = new com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory(player)
		{
			@Override
			public void refreshWeight()
			{
				// The fixture exercises publication, not the player's weight callbacks.
			}
		};
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
			EffectLedger.record(player, BridgeEffect.coin(-13));
			assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.DONE);
			inventory.publishCommittedMultisell(java.util.Map.of(), java.util.List.of());
			assertThat(attempt.isLedgerIncomplete()).isTrue();
			assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void partialSalesAndPaidZeroRemainUnknown()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, -1));
			assertThat(BridgeOutcome.match(attempt, 8, 1060, 70, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
			EffectLedger.record(player, BridgeEffect.coin(5));
			assertThat(BridgeOutcome.match(attempt, 8, 1060, 70, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
			EffectLedger.record(player, BridgeEffect.item(1060, 70, -1));
			assertThat(BridgeOutcome.match(attempt, 8, 1060, 70, 2)).isEqualTo(BridgeOutcome.DONE);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void foreignOwnerAndOtherThreadCannotSupplyThePurchaseReceipt() throws Exception
	{
		var player = BridgeTestPlayers.player();
		var other = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.record(other, BridgeEffect.item(1060, 70, 2));
			Thread worker = new Thread(() -> EffectLedger.record(player, BridgeEffect.coin(-13)));
			worker.start();
			worker.join();
			assertThat(attempt.effects()).isEmpty();
			assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void learningRequiresTheSelectedCostAndRejectsExtraOrMissingReceipts()
	{
		L2Player player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.expectLearningCost(player, 0, 10, 100, 1);
			EffectLedger.record(player, BridgeEffect.skill(12, 0, 1));
			EffectLedger.record(player, BridgeEffect.sp(-10, 20, 10));
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
			EffectLedger.record(player, BridgeEffect.item(100, 70, -1));
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.DONE);
			EffectLedger.record(player, BridgeEffect.target(7));
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void aTaxedPurchaseMatchesActualCoinWithoutAnObservedPrice()
	{
		L2Player player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
			EffectLedger.record(player, BridgeEffect.coin(-13));
			assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.DONE);
			EffectLedger.record(player, BridgeEffect.coin(13));
			assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void anUnregisteredWhiteRefusalIsUnknownNotDoneOrRefused()
	{
		Attempt attempt = new Attempt(BridgeTestPlayers.player());
		attempt.addRefusal(279);
		assertThat(BridgeOutcome.match(attempt, 7, 1060, 0, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
		assertThat(BridgeOutcome.match(attempt, 8, 1060, 70, 2)).isEqualTo(BridgeOutcome.UNKNOWN);
	}

	@Test
	void failureDominatesReceiptsAndMovementNeverGetsVacuousDone()
	{
		Attempt attempt = new Attempt(BridgeTestPlayers.player());
		assertThat(BridgeOutcome.match(attempt, 1, 0, 0, 0)).isEqualTo(BridgeOutcome.SENT);
		attempt.markFailed();
		assertThat(BridgeOutcome.match(attempt, 1, 0, 0, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
	}
}
