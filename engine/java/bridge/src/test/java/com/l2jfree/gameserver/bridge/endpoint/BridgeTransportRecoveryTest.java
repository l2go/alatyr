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
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.server.*;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.embedded.EmbeddedChannel;

/** Uses the real client FIFO and listener socket branches with isolated game state. */
class BridgeTransportRecoveryTest
{
	@Test
	void relocationDuringOpeningKeepsTheQueueFreeAndEndsOnlyAfterCompletion() throws Exception
	{
		try (Fixture fixture = new Fixture(19930))
		{
			var stable = new java.util.concurrent.atomic.AtomicBoolean(false);
			doAnswer(call ->
			{
				if (!stable.get()) return false;
				((Runnable)call.getArgument(0)).run();
				return true;
			}).when(fixture.player).captureBridgeScene(any(Runnable.class));
			fixture.seed();
			fixture.owner.sink.deliver(TeleportToLocation.forBridgeRelocation(fixture.player, 1, 2, 3, 0, 1), fixture.player);
			fixture.client.submit(() -> fixture.owner.opened(BridgeFrames.accepted(fixture.id, 1)));
			barrier(fixture.client);
			assertThat(fixture.first.checkpoint).isNull();
			assertThat(fixture.first.queued).isEmpty();
			doAnswer(call ->
			{
				stable.set(true);
				fixture.owner.sink.deliver(new UserInfo(fixture.player), fixture.player);
				return null;
			}).when(fixture.player).completeBridgeTeleport(1);
			fixture.owner.sink.ownTeleported(1);
			barrier(fixture.client);
			var records = fixture.drain(fixture.first);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(Schemas.BEGIN);
			int corrected = -1;
			int appeared = -1;
			for (int i = 0; i < records.size(); i++)
			{
				if (records.get(i).type() != Schemas.FACT) continue;
				if (records.get(i).field(67) != null) corrected = i;
				if (records.get(i).field(19) != null && records.get(i).field(19).value().u() == 1) appeared = i;
			}
			assertThat(corrected).isGreaterThan(appeared);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.get(records.size() - 1).type()).isEqualTo(Schemas.OPENED);
			verify(fixture.player).completeBridgeTeleport(1);
		}
	}

	@Test
	void eofRetainsTheCharacterAndResumeWritesACompleteCheckpointBeforeOpened() throws Exception
	{
		try (Fixture fixture = new Fixture(19890))
		{
			fixture.open();
			SocketChannel lost = mock(SocketChannel.class);
			when(lost.read(any(ByteBuffer.class))).thenReturn(-1);
			fixture.loop.account();
			fixture.loop.serve(key(lost, fixture.first, true));
			verify(lost).close();
			assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
			verify(fixture.player, never()).store(true, true);
			var replacement = new BridgeLoopback.Transport(new byte[32]);
			fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
			barrier(fixture.client);
			var records = fixture.drain(replacement);
			assertThat(records.get(0).type()).isEqualTo(Schemas.CHECKPOINT);
			assertThat(records.get(0).field(1).value().u()).isEqualTo(Schemas.BEGIN);
			assertThat(records.get(records.size() - 2).field(3).value().u()).isEqualTo(1);
			assertThat(records.get(records.size() - 1).type()).isEqualTo(Schemas.OPENED);
			assertThat(records.get(records.size() - 1).field(2).value().u()).isEqualTo(fixture.id);
			assertThat(replacement.client).isSameAs(fixture.client);
			assertThat(fixture.client.getActiveChar()).isSameAs(fixture.player);
			verify(fixture.player, never()).store(true, true);
		}
	}

	@Test
	void oldQueuedWorkIsRefusedBeforeResumeAndReplayedOnlyAfterTheSnapshot() throws Exception
	{
		try (Fixture fixture = new Fixture(19910))
		{
			fixture.open();
			CountDownLatch running = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			fixture.client.submit(() ->
			{
				running.countDown();
				try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("queue gate"); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
			});
			assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
			try
			{
				// Unknown essential rise fixture: admission is real, but stale execution must never reach its body.
				var intent = Codec.bridge().decode(java.util.HexFormat.of().parseHex("0a09180128093801b80c01")).get(0);
				fixture.first.attempts.accept(fixture.client, intent, fixture.first.attemptFence);
				SocketChannel lost = mock(SocketChannel.class);
				when(lost.read(any(ByteBuffer.class))).thenReturn(-1);
				fixture.loop.account();
				fixture.loop.serve(key(lost, fixture.first, true));
				var replacement = new BridgeLoopback.Transport(new byte[32]);
				fixture.loop.acceptFrame(replacement, List.of(resume(fixture.id, 2)));
				release.countDown();
				barrier(fixture.client);
				var records = fixture.drain(replacement);
				int result = -1;
				int end = -1;
				for (int i = 0; i < records.size(); i++)
				{
					if (records.get(i).type() == Schemas.RESULT) result = i;
					if (records.get(i).type() == Schemas.CHECKPOINT && records.get(i).field(1).value().u() == Schemas.END) end = i;
				}
				assertThat(result).isGreaterThan(end);
				assertThat(records.get(result).field(65).value().u()).isEqualTo(9);
				assertThat(records.get(result).field(2).value().u()).isEqualTo(3);
				assertThat(fixture.client.isBridgeDisconnectRequested()).isFalse();
			}
			finally { release.countDown(); }
		}
	}

	private static Envelope resume(long id, long epoch)
	{
		return Envelope.of(Schemas.RESUME, List.of(Field.u(1, true, id), Field.u(2, true, epoch),
				Field.data(3, true, new byte[32]), Field.data(4, true, Codec.profileName())));
	}

	private static void barrier(L2Client client) throws Exception
	{
		CountDownLatch completed = new CountDownLatch(1);
		client.submit(completed::countDown);
		assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
	}

	private static SelectionKey key(SocketChannel channel, BridgeLoopback.Transport transport, boolean read)
	{
		SelectionKey key = mock(SelectionKey.class);
		when(key.isValid()).thenReturn(true);
		when(key.isReadable()).thenReturn(read);
		when(key.channel()).thenReturn(channel);
		when(key.attachment()).thenReturn(transport);
		return key;
	}

	private static final class Fixture implements AutoCloseable
	{
		final InlinePool pool = InlinePool.open();
		final EmbeddedChannel channel = new EmbeddedChannel();
		final BridgeLoopback loop;
		final BridgeLoopback.Transport first = new BridgeLoopback.Transport(new byte[32]);
		final BridgeLoopback.Session owner;
		final L2Client client;
		final L2Player player = BridgeTestPlayers.player();
		final long id;

		@SuppressWarnings("unchecked")
		Fixture(int port) throws Exception
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", "44".repeat(32));
			loop = BridgeLoopback.open(BridgeSettings.parse(properties), null);
			owner = loop.new Session(first);
			client = L2Client.forBridge(mock(NetworkServer.class), channel, InetAddress.getLoopbackAddress(), owner.sink);
			owner.sink.attach(client);
			owner.attach(client);
			id = BridgeAdmission.newSession(client);
			client.setActiveChar(player);
			client.setState(L2Client.GameClientState.IN_GAME);
			when(player.getObjectId()).thenReturn(70);
			when(player.getView()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.view.PlayerView.class));
			when(player.getView().getObjectId()).thenReturn(70);
			when(player.getView().getMovementSpeedMultiplier()).thenReturn(1.0f);
			when(player.getAppearance()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance.class));
			when(player.getAppearance().getVisibleName()).thenReturn("Visible player");
			when(player.getStat()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.stat.PlayerStat.class));
			when(player.getStatus()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.status.PlayerStatus.class));
			when(player.getSortedAllSkills(anyBoolean())).thenReturn(new com.l2jfree.gameserver.model.skills.L2Skill[0]);
			when(player.store(true, true)).thenReturn(true);
			doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return true; }).when(player).captureBridgeScene(any(Runnable.class));
		}

		void open() throws Exception
		{
			seed();
			client.submit(() -> owner.opened(BridgeFrames.accepted(id, 1)));
			barrier(client);
			assertThat(drain(first)).isNotEmpty();
		}

		void seed()
		{
			first.holdOpeningFacts = true;
			owner.sink.deliver(new UserInfo(player), player);
			owner.sink.deliver(new ItemList(new com.l2jfree.gameserver.model.items.L2ItemInstance[0], false), player);
			owner.sink.deliver(new SkillList(player), player);
			owner.sink.deliver(new QuestList(player), player);
		}

		List<Envelope> drain(BridgeLoopback.Transport transport) throws Exception
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			SocketChannel socket = mock(SocketChannel.class);
			when(socket.write(any(ByteBuffer.class))).thenAnswer(call ->
			{
				ByteBuffer value = call.getArgument(0);
				byte[] copy = new byte[value.remaining()];
				value.get(copy);
				bytes.write(copy);
				return copy.length;
			});
			loop.serve(key(socket, transport, false));
			barrier(client);
			loop.serve(key(socket, transport, false));
			var result = new ArrayList<Envelope>();
			var input = new ByteArrayInputStream(bytes.toByteArray());
			while (input.available() != 0)
				result.addAll(Codec.bridge().read(input));
			return result;
		}

		@Override
		public void close() throws Exception
		{
			client.requestBridgeDisconnect();
			barrier(client);
			loop.close();
			channel.finishAndReleaseAll();
			pool.close();
			BridgeAdmission.reapSessions();
		}
	}
}
