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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

class BridgeOutcomeTest
{
	@Test
	void anAlreadyKnownLearningLevelCannotProduceANewDone()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.expectLearningCost(player, 0, 0, 0, 0);
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
			EffectLedger.record(player, BridgeEffect.skill(12, 1, 2));
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
			assertThat(BridgeOutcome.learn(attempt, 12, 2)).isEqualTo(BridgeOutcome.DONE);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void synchronousDialogueFromTheTargetNpcSurvivesAFWithoutAcceptingOtherPanels()
	{
		var player = BridgeTestPlayers.player();
		var npc = org.mockito.Mockito.mock(com.l2jfree.gameserver.gameobjects.L2Npc.class);
		org.mockito.Mockito.when(npc.getObjectId()).thenReturn(70);
		org.mockito.Mockito.when(player.getLastFolkNPC()).thenReturn(npc);
		org.mockito.Mockito.when(player.getTarget()).thenReturn(npc);
		var client = org.mockito.Mockito.mock(com.l2jfree.gameserver.network.L2Client.class);
		org.mockito.Mockito.when(client.isBridge()).thenReturn(true);
		org.mockito.Mockito.when(client.getActiveChar()).thenReturn(player);
		var sink = new BridgeSink(records -> { });
		sink.attach(client);
		Attempt attempt = new Attempt(client, player);
		EffectLedger.open(attempt);
		try
		{
			var otherClient = org.mockito.Mockito.mock(com.l2jfree.gameserver.network.L2Client.class);
			org.mockito.Mockito.when(otherClient.isBridge()).thenReturn(true);
			org.mockito.Mockito.when(otherClient.getActiveChar()).thenReturn(player);
			var otherSink = new BridgeSink(records -> { });
			otherSink.attach(otherClient);
			otherSink.deliver(new com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage(70, "other session"), player);
			assertThat(BridgeOutcome.match(attempt, 5, 0, 70, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
			sink.deliver(new com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage(71, "other"), player);
			assertThat(BridgeOutcome.match(attempt, 5, 0, 70, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
			sink.deliver(new com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage(70, "dialogue"), player);
			sink.deliver(com.l2jfree.gameserver.network.packets.server.ActionFailed.STATIC_PACKET, player);
			assertThat(BridgeOutcome.match(attempt, 5, 0, 70, 0)).isEqualTo(BridgeOutcome.DONE);
			assertThat(BridgeOutcome.match(attempt, 6, 0, 70, 0)).isEqualTo(BridgeOutcome.DONE);
			assertThat(BridgeOutcome.match(attempt, 5, 0, 71, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void identicalLearningMetadataDoesNotDuplicateCostButAChangedPlanIsIncomplete()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.expectLearningCost(player, 0, 10, 100, 1);
			EffectLedger.expectLearningCost(player, 0, 10, 100, 1);
			assertThat(attempt.hasLearningCost()).isTrue();
			assertThat(attempt.isLedgerIncomplete()).isFalse();
			assertThat(attempt.effects()).isEmpty();
			EffectLedger.expectLearningCost(player, 0, 11, 100, 1);
			assertThat(attempt.isLedgerIncomplete()).isTrue();
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void aClassSwitchInvalidatesAllAttemptOutcomes()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		org.mockito.Mockito.when(player.getClassIndex()).thenReturn(1);
		assertThat(attempt.isLedgerIncomplete()).isTrue();
		assertThat(BridgeOutcome.match(attempt, 9, 0, 0, 0)).isEqualTo(BridgeOutcome.UNKNOWN);
	}

	@Test
	void learningCoinCostRequiresOneExactReceipt()
	{
		var player = BridgeTestPlayers.player();
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			EffectLedger.expectLearningCost(player, 0, 0, 57, 10);
			EffectLedger.record(player, BridgeEffect.skill(12, 0, 1));
			EffectLedger.record(player, BridgeEffect.coin(-4));
			EffectLedger.record(player, BridgeEffect.coin(-6));
			assertThat(BridgeOutcome.learn(attempt, 12, 1)).isEqualTo(BridgeOutcome.UNKNOWN);
		}
		finally
		{
			EffectLedger.close();
		}
	}

	@Test
	void committedMultisellMarksTheOwnerLedgerIncompleteAndUnknown()
	{
		var player = BridgeTestPlayers.player();
		var item = org.mockito.Mockito.mock(com.l2jfree.gameserver.model.items.L2ItemInstance.class);
		org.mockito.Mockito.when(item.getCount()).thenReturn(3L);
		var inventory = new com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory(player)
		{
			{
				addItem(item);
			}
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
			inventory.publishCommittedMultisell(java.util.Map.of(item, 2L), java.util.List.of());
			org.mockito.Mockito.verify(item).setCount(2L);
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
