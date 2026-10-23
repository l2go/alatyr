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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.embedded.EmbeddedChannel;

class BridgeDescribeLifecycleTest
{
	@SuppressWarnings("unchecked")
	private static L2Client client(BridgeSink sink, EmbeddedChannel channel) throws Exception
	{
		NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
		var client = L2Client.forBridge(server, channel, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), sink);
		sink.attach(client);
		return client;
	}

	@Test
	void translatedAndNonessentialDeliveryKeepCallbacksInOrderAndNeverEncodeNc() throws Exception
	{
		List<List<Envelope>> written = new ArrayList<>();
		var sink = new BridgeSink(written::add);
		var channel = new EmbeddedChannel();
		try
		{
			var client = client(sink, channel);
			var player = BridgeTestPlayers.player();
			client.setActiveChar(player);
			var translated = spy(new StopMove(10, 1, 2, 3, 0));
			var nonessential = mock(ActionFailed.class, CALLS_REAL_METHODS);
			client.sendPacket(translated);
			client.sendPacket(nonessential);
			var order = inOrder(translated, nonessential);
			order.verify(translated).canBeSentTo(client, player);
			order.verify(translated).prepareToSend(client, player);
			order.verify(translated).describe(sinkStage(), org.mockito.ArgumentMatchers.eq(player));
			order.verify(translated).packetSent(client, player);
			order.verify(nonessential).canBeSentTo(client, player);
			order.verify(nonessential).prepareToSend(client, player);
			order.verify(nonessential).packetSent(client, player);
			assertThat(written).hasSize(1);
			assertThat(written.get(0).get(0).type()).isEqualTo(Schemas.FACT);
			assertThat(channel.outboundMessages()).isNullOrEmpty();
		}
		finally { channel.finishAndReleaseAll(); }
	}

	private static com.l2jfree.gameserver.network.bridge.FactSink sinkStage()
	{
		return org.mockito.ArgumentMatchers.any(com.l2jfree.gameserver.network.bridge.FactSink.class);
	}

	@Test
	void fatalPacketRunsFullSaveAndDeleteAndKeepsInternalAfterASuccessfulSave() throws Exception
	{
		CountDownLatch bye = new CountDownLatch(1);
		List<List<Envelope>> written = new ArrayList<>();
		var sink = new BridgeSink(records ->
		{
			written.add(records);
			bye.countDown();
		});
		var channel = new EmbeddedChannel();
		try (InlinePool pool = InlinePool.open())
		{
			var client = client(sink, channel);
			var player = BridgeTestPlayers.player();
			when(player.store(true, true)).thenReturn(true);
			client.setActiveChar(player);
			client.sendPacket(new StopMove(10, 1, 2, 3, 0) {});
			assertThat(bye.await(5, TimeUnit.SECONDS)).isTrue();
			var order = inOrder(player);
			order.verify(player).detachBridge(client);
			order.verify(player).store(true, true);
			order.verify(player).deleteMe();
			assertThat(written).hasSize(1);
			assertThat(written.get(0).get(0).type()).isEqualTo(Schemas.BYE);
			assertThat(written.get(0).get(0).field(1).value().u()).isEqualTo(7);
			CountDownLatch finished = new CountDownLatch(1);
			client.submit(finished::countDown);
			assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(client.isBridgeDisconnectFinished()).isTrue();
		}
		finally { channel.finishAndReleaseAll(); }
	}

	@Test
	void aFactFromTheSinkDoesNotClaimTheTerminalBye() throws Exception
	{
		Properties properties = new Properties();
		properties.setProperty("bridge.enabled", "true");
		properties.setProperty("bridge.port", "19885");
		properties.setProperty("bridge.secret", "44".repeat(32));
		var loop = BridgeLoopback.open(BridgeSettings.parse(properties), null);
		try
		{
			var transport = new BridgeLoopback.Transport(new byte[32]);
			var sink = new BridgeSink(records -> loop.fromSink(transport, records));
			var client = mock(L2Client.class);
			when(client.isBridge()).thenReturn(true);
			sink.attach(client);
			sink.deliver(new StopMove(10, 1, 2, 3, 0), null);
			assertThat(transport.queued).hasSize(1);
			assertThat(transport.terminal).isFalse();
			assertThat(transport.closing).isFalse();
			sink.disconnect(com.l2jfree.gameserver.network.bridge.BridgeFailure.normal());
			loop.serviceControl(transport);
			BridgeLoopback.applyOutputFlags(transport);
			assertThat(transport.queued).hasSize(2);
			assertThat(transport.terminal).isTrue();
		}
		finally { loop.close(); }
	}

	@Test
	void openingObservationsFollowBeginAndPrecedeTheExistingNotReadyEnd() throws Exception
	{
		Properties properties = new Properties();
		properties.setProperty("bridge.enabled", "true");
		properties.setProperty("bridge.port", "19886");
		properties.setProperty("bridge.secret", "44".repeat(32));
		var loop = BridgeLoopback.open(BridgeSettings.parse(properties), null);
		try
		{
			var transport = new BridgeLoopback.Transport(new byte[32]);
			transport.holdOpeningFacts = true;
			var sink = new BridgeSink(records -> loop.fromSink(transport, records));
			var client = mock(L2Client.class);
			when(client.isBridge()).thenReturn(true);
			sink.attach(client);
			sink.deliver(new StopMove(10, 1, 2, 3, 0), null);
			assertThat(transport.queued).isEmpty();
			assertThat(transport.openingFacts).hasSize(1);
			loop.fromSink(transport, BridgeFrames.accepted(7, 1));
			assertThat(transport.openingFacts).isEmpty();
			assertThat(transport.openingFactBytes).isZero();
			var decoded = new ArrayList<Envelope>();
			for (byte[] frame : transport.queued)
				decoded.addAll(com.l2jfree.gameserver.bridge.wire.Codec.bridge()
						.read(new java.io.ByteArrayInputStream(frame)));
			assertThat(decoded).extracting(Envelope::type).containsExactly(
					Schemas.CHECKPOINT, Schemas.FACT, Schemas.CHECKPOINT, Schemas.OPENED);
			assertThat(decoded.get(2).field(3).value().u()).isZero();
			assertThat(transport.terminal).isFalse();
		}
		finally { loop.close(); }
	}
}
