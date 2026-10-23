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
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.packets.server.*;

class BridgeDeliveryTest
{
	private final List<List<Envelope>> records = new ArrayList<>();
	private final L2Player player = BridgeTestPlayers.player();
	private final L2Client client = mock(L2Client.class);
	private final BridgeSink sink = new BridgeSink(records::add);

	BridgeDeliveryTest()
	{
		when(client.isBridge()).thenReturn(true);
		when(client.getActiveChar()).thenReturn(player);
		when(player.getObjectId()).thenReturn(10);
		sink.attach(client);
	}

	@Test
	void spokeFollowedByActionFailedIsDoneButNoticesNeverCertifyTalk()
	{
		L2Npc npc = mock(L2Npc.class);
		when(npc.getObjectId()).thenReturn(70);
		when(player.getLastFolkNPC()).thenReturn(npc);
		when(player.getTarget()).thenReturn(npc);
		var attempt = new Attempt(client, player);
		EffectLedger.open(attempt);
		try
		{
			sink.deliver(new NpcSay(70, 0, 1, "A notice"), player);
			assertThat(attempt.hasSpoken()).isFalse();
			sink.deliver(new NpcHtmlMessage(70, "<a action=\"bypass -h npc_70_Chat 1\">Continue</a>"), player);
			sink.deliver(ActionFailed.STATIC_PACKET, player);
			assertThat(attempt.hasSpoken(70)).isTrue();
			assertThat(BridgeOutcome.match(attempt, 5, 0, 70, 0)).isEqualTo(BridgeOutcome.DONE);
			assertThat(records).hasSize(2);
			assertThat(records.get(1).get(0).field(68).value().items()).hasSize(1);
			verify(client, never()).requestBridgeDisconnect(any(BridgeFailure.class));
		}
		finally { EffectLedger.close(); }
	}

	@Test
	void malformedHtmlPublishesNoPartialWindowAndRequestsFullInternalCleanup()
	{
		var attempt = new Attempt(client, player);
		EffectLedger.open(attempt);
		try
		{
			sink.deliver(new NpcHtmlMessage(70, "<button action=\"bypass -h good\" value=\"Good\"><a action=\"unfinished"), player);
			assertThat(records).isEmpty();
			assertThat(attempt.hasSpoken()).isFalse();
			assertThat(attempt.isLedgerIncomplete()).isTrue();
			verify(client).requestBridgeDisconnect(BridgeFailure.internal());
			sink.deliver(new StopMove(10, 1, 2, 3, 0), player);
			assertThat(records).isEmpty();
		}
		finally { EffectLedger.close(); }
	}

	@Test
	void anUnregisteredSubtypeIsFatalEvenWhenItsParentIsTranslated()
	{
		sink.deliver(new StopMove(10, 1, 2, 3, 0) {}, player);
		assertThat(records).isEmpty();
		verify(client).requestBridgeDisconnect(BridgeFailure.internal());
	}

	@Test
	void deliveredRefusalsBelongOnlyToTheSameClientOwnerAndThread() throws Exception
	{
		var attempt = new Attempt(client, player);
		EffectLedger.open(attempt);
		try
		{
			L2Player other = BridgeTestPlayers.player();
			var refusal = new SystemMessage(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			sink.deliver(refusal, other);
			assertThat(attempt.refusals()).isEmpty();
			CountDownLatch delivered = new CountDownLatch(1);
			Thread thread = new Thread(() ->
			{
				try { sink.deliver(refusal, player); }
				finally { delivered.countDown(); }
			});
			thread.start();
			assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
			thread.join(5000);
			assertThat(thread.isAlive()).isFalse();
			assertThat(attempt.refusals()).isEmpty();
			sink.deliver(refusal, player);
			assertThat(attempt.refusals()).containsExactly(279);
			var result = Codec.bridge().decode(BridgeResults.encode(1, 7, BridgeOutcome.UNKNOWN, 0, attempt)).get(0);
			assertThat(result.field(2).value().u()).isEqualTo(3);
			assertThat(result.field(3).value().u()).isEqualTo(16);
			assertThat(result.field(65).value().u()).isEqualTo(1);
		}
		finally { EffectLedger.close(); }
	}

	@Test
	void exactTypedAwardCarriesAmountsAndAdenaIsOnlyANotice()
	{
		var message = new SystemMessage(SystemMessageId.YOU_EARNED_S1_EXP_AND_S2_SP);
		message.addExpNumber(1234567890123L);
		message.addNumber(17);
		sink.deliver(message, player);
		assertThat(records).hasSize(2);
		var reward = records.get(1).get(0);
		assertThat(reward.field(1).value().u()).isEqualTo(17);
		assertThat(reward.field(25).value().fields().get(0).value().s()).isEqualTo(1234567890123L);
		assertThat(reward.field(25).value().fields().get(1).value().s()).isEqualTo(17);
		var adena = new SystemMessage(SystemMessageId.EARNED_S1_ADENA);
		adena.addNumber(10);
		sink.deliver(adena, player);
		assertThat(records).hasSize(3);
		assertThat(records.get(2).get(0).field(1).value().u()).isEqualTo(18);
	}

	@Test
	void malformedTypedAwardAndInvalidUtf8CloseWithoutPublishing()
	{
		var invalid = new SystemMessage(SystemMessageId.EARNED_S1_EXPERIENCE);
		invalid.addString("not an amount");
		sink.deliver(invalid, player);
		assertThat(records).isEmpty();
		verify(client).requestBridgeDisconnect(BridgeFailure.internal());
		var anotherClient = mock(L2Client.class);
		when(anotherClient.isBridge()).thenReturn(true);
		var another = new BridgeSink(records::add);
		another.attach(anotherClient);
		another.deliver(new ExShowScreenMessage("\uD800", 100), null);
		assertThat(records).isEmpty();
		verify(anotherClient).requestBridgeDisconnect(BridgeFailure.internal());
	}

	@Test
	void movementModeDoesNotReadHiddenStatsAndGoneDiscardsKnownSpeed()
	{
		var actor = mock(com.l2jfree.gameserver.gameobjects.L2Creature.class);
		when(actor.getObjectId()).thenReturn(70);
		when(actor.isRunning()).thenReturn(true);
		sink.deliver(new ChangeMoveType(actor), player);
		assertThat(records.get(0).get(0).field(15)).isNull();
		verify(actor, never()).getStat();
		sink.deliver(new StopMove(70, 1, 2, 3, 0), player);
		sink.deliver(new ChangeMoveType(actor), player);
		assertThat(records.get(2).get(0).field(13).value().x()).isEqualTo(1);
		sink.deliver(new DeleteObject(actor), player);
		sink.deliver(new ChangeMoveType(actor), player);
		assertThat(records.get(4).get(0).field(13)).isNull();
		assertThat(records.get(4).get(0).field(15)).isNull();
		long last = 0;
		for (var batch : records)
		{
			long sequence = batch.get(0).field(2).value().u();
			assertThat(sequence).isGreaterThan(last);
			last = sequence;
		}
	}
}
