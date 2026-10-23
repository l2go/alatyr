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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Field;
import com.l2jfree.gameserver.bridge.wire.Schemas;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A bound close sends the bye that the disconnect save produced, and only that bye.
 */
class BridgeBoundCloseTest
{
	@Test
	void storeCloseAndStoreDeleteWaitForCurrentWorldWorkAndDisconnectOnce() throws Exception
	{
		for (boolean delete : new boolean[] { false, true })
		{
			CountDownLatch entered = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			try (InlinePool pool = InlinePool.open(); Bound bound = Bound.open(call -> true))
			{
				bound.client.submit(() ->
				{
					entered.countDown();
					try
					{
						assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
					}
					catch (InterruptedException interrupted)
					{
						Thread.currentThread().interrupt();
						throw new AssertionError(interrupted);
					}
				});
				try
				{
					assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
					var facade = new com.l2jfree.gameserver.network.Disconnection(bound.client).store();
					if (delete)
						facade.deleteMe();
					else
						facade.close(false);
					new com.l2jfree.gameserver.network.Disconnection(bound.client).store().deleteMe().close(false);
					verify(bound.player, org.mockito.Mockito.never()).store(true, true);
					verify(bound.player, org.mockito.Mockito.never()).deleteMe();
				}
				finally
				{
					release.countDown();
				}
				bound.awaitDisconnect();
				verify(bound.player, times(1)).store(true, true);
				verify(bound.player, times(1)).deleteMe();
				assertThat(bound.transport.queued).hasSize(1);
			}
		}
	}

	@Test
	void aSavedCharacterClosesNormally() throws Exception
	{
		assertBye(true, BridgeFrames.BYE_NORMAL);
	}
	
	@Test
	void aRejectedSaveClosesInternally() throws Exception
	{
		assertBye(false, BridgeFrames.BYE_INTERNAL);
	}
	
	@Test
	void aSaveThatThrowsClosesInternally() throws Exception
	{
		assertBye(new RuntimeException("disk"), BridgeFrames.BYE_INTERNAL);
	}

	@Test
	void aRepeatedOpenKeepsItsFrameErrorAfterASuccessfulSave() throws Exception
	{
		assertProtocolBye(true);
	}

	@Test
	void aRepeatedOpenKeepsItsFrameErrorAfterAFailedSave() throws Exception
	{
		assertProtocolBye(false);
	}

	private static void assertProtocolBye(boolean saved) throws Exception
	{
		CountDownLatch inStore = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (InlinePool pool = InlinePool.open(); Bound bound = Bound.open(invocation ->
		{
			inStore.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return saved;
		}))
		{
			try
			{
				Envelope repeatedOpen = openFrame(new byte[32]);
				bound.loop.acceptFrame(bound.transport, List.of(repeatedOpen));
				assertThat(inStore.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(bound.transport.queued).hasSize(1);
				assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_FRAME);
				bound.loop.acceptFrame(bound.transport, List.of(repeatedOpen));
			}
			finally
			{
				release.countDown();
			}
			bound.awaitDisconnect();
			assertThat(bound.transport.queued).hasSize(1);
			assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_FRAME);
			verify(bound.player, times(1)).store(true, true);
			verify(bound.player, times(1)).deleteMe();
		}
	}
	
	@Test
	void aSaveAfterTheDeadlineClosesInternallyBeforeTheTimerRuns() throws Exception
	{
		CountDownLatch inStore = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (InlinePool pool = InlinePool.open(); Bound bound = Bound.open(invocation ->
		{
			inStore.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return true;
		}))
		{
			try
			{
				bound.closeOnce();
				assertThat(inStore.await(5, TimeUnit.SECONDS)).isTrue();
				synchronized (bound.transport)
				{
					bound.transport.resultDeadline = System.nanoTime() - 1;
				}
				bound.closeOnce();
				assertThat(bound.transport.queued).isEmpty();
			}
			finally
			{
				release.countDown();
			}
			bound.awaitDisconnect();
			assertThat(bound.transport.queued).hasSize(1);
			assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_INTERNAL);
			BridgeLoopback.expire(bound.transport);
			assertThat(bound.transport.queued).hasSize(1);
			assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_INTERNAL);
			verify(bound.player, times(1)).store(true, true);
			verify(bound.player, times(1)).deleteMe();
		}
	}

	@Test
	void aMissedSaveDeadlineClosesInternally() throws Exception
	{
		CountDownLatch inStore = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (InlinePool pool = InlinePool.open(); Bound bound = Bound.open(invocation ->
		{
			inStore.countDown();
			assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
			return true;
		}))
		{
			bound.closeOnce();
			assertThat(inStore.await(5, TimeUnit.SECONDS)).isTrue();
			bound.transport.resultDeadline = 1;
			BridgeLoopback.expire(bound.transport);
			assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_INTERNAL);
			release.countDown();
			Thread.sleep(200);
			assertThat(bound.transport.queued).hasSize(1);
			assertThat(reason(bound.transport)).isEqualTo(BridgeFrames.BYE_INTERNAL);
			verify(bound.player, times(1)).store(true, true);
		}
	}
	
	private static void assertBye(boolean saved, long reason) throws Exception
	{
		assertBye(invocation -> saved, reason);
	}
	
	private static void assertBye(RuntimeException failure, long reason) throws Exception
	{
		assertBye(invocation ->
		{
			throw failure;
		}, reason);
	}
	
	private static void assertBye(org.mockito.stubbing.Answer<Boolean> store, long reason) throws Exception
	{
		try (InlinePool pool = InlinePool.open(); Bound bound = Bound.open(store))
		{
			bound.closeOnce();
			long end = System.nanoTime() + 5_000_000_000L;
			while (bound.transport.queued.isEmpty() && System.nanoTime() < end)
				Thread.sleep(10);
			assertThat(reason(bound.transport)).isEqualTo(reason);
			assertThat(bound.transport.queued).hasSize(1);
			bound.closeOnce();
			Thread.sleep(100);
			assertThat(bound.transport.queued).hasSize(1);
			verify(bound.player, times(1)).store(true, true);
			assertThat(bound.client.isBridgeDisconnectRequested()).isTrue();
		}
	}
	
	private static long reason(Transport transport) throws Exception
	{
		byte[] frame = transport.queued.peek();
		List<Envelope> records = Codec.bridge().read(new ByteArrayInputStream(frame));
		return records.get(0).field(1).value().u();
	}
	
	private static final class Bound implements AutoCloseable
	{
		private final MockedStatic<SkillTreeTable> tables;
		private final BridgeLoopback loop;
		private final Transport transport;
		private final L2Client client;
		private final L2Player player;
		
		private Bound(MockedStatic<SkillTreeTable> tables, BridgeLoopback loop, Transport transport, L2Client client,
				L2Player player)
		{
			this.tables = tables;
			this.loop = loop;
			this.transport = transport;
			this.client = client;
			this.player = player;
		}
		
		static Bound open(org.mockito.stubbing.Answer<Boolean> store) throws Exception
		{
			MockedStatic<SkillTreeTable> tables = mockStatic(SkillTreeTable.class);
			SkillTreeTable tree = mock(SkillTreeTable.class);
			when(tree.getExpertiseLevel(anyInt())).thenReturn(-1);
			tables.when(SkillTreeTable::getInstance).thenReturn(tree);
			L2Player player = mock(L2Player.class);
			when(player.store(true, true)).thenAnswer(store);
			byte[] secret = new byte[32];
			for (int i = 0; i < secret.length; i++)
				secret[i] = (byte) (i + 3);
			Transport transport = new Transport(secret);
			transport.dialogue.accept(List.of(openFrame(secret)));
			BridgeLoopback loop = listener();
			@SuppressWarnings("unchecked")
			NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> server = mock(NetworkServer.class);
			BridgeSink sink = new BridgeSink(records -> loop.fromSink(transport, records));
			L2Client client = L2Client.forBridge(server, new EmbeddedChannel(),
					InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), sink);
			sink.attach(client);
			client.setActiveChar(player);
			transport.client = client;
			return new Bound(tables, loop, transport, client, player);
		}
		
		void closeOnce() throws Exception
		{
			Envelope close = Envelope.of(Schemas.CLOSE, List.of(Field.u(1, true, 1), Field.u(2, true, 1), Field.u(3, true, 0)));
			loop.acceptFrame(transport, List.of(close));
		}
		
		void awaitDisconnect() throws Exception
		{
			long end = System.nanoTime() + 5_000_000_000L;
			while (!client.isBridgeDisconnectFinished() && System.nanoTime() < end)
				Thread.sleep(10);
			assertThat(client.isBridgeDisconnectFinished()).isTrue();
		}

		@Override
		public void close()
		{
			loop.close();
			tables.close();
		}
	}
	
	private static Envelope openFrame(byte[] secret)
	{
		return Envelope.of(Schemas.OPEN, List.of(Field.u(1, true, 1), Field.u(2, true, 1),
				Field.data(3, true, Codec.profileName()), Field.data(4, true, secret),
				Field.data(5, true, "guest".getBytes(StandardCharsets.UTF_8)),
				Field.data(6, true, "Guest".getBytes(StandardCharsets.UTF_8)), Field.u(7, true, 1)));
	}
	
	private static BridgeLoopback listener()
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < 32; i++)
			hex.append("55");
		for (int port = 19540; port < 19560; port++)
		{
			Properties properties = new Properties();
			properties.setProperty("bridge.enabled", "true");
			properties.setProperty("bridge.port", Integer.toString(port));
			properties.setProperty("bridge.secret", hex.toString());
			try
			{
				return BridgeLoopback.open(BridgeSettings.parse(properties), null);
			}
			catch (IllegalStateException e)
			{
				// try the next port
			}
		}
		throw new IllegalStateException("no port");
	}
}
