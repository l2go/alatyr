/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. This program is distributed without any warranty.
 * See <http://www.gnu.org/licenses/> for details.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.TradeListTable;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.MercTicketManager;
import com.l2jfree.gameserver.model.L2TradeList;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.packets.client.RequestBuyItem;

/** Runs the existing purchase handler with isolated world dependencies, without transport recovery. */
class BridgePurchaseExecutionTest
{
	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void liveDuplicateRunsTheActualPurchaseHandlerOnce(int scenario)
	{
		runActualPurchase(scenario);
	}

	@ParameterizedTest
	@ValueSource(ints = { 3, 4, 5, 6 })
	void socketLossBeforeDuringAndAfterPurchaseResumesWithoutBuyingTwice(int scenario)
	{
		runActualPurchase(scenario);
	}

	private void runActualPurchase(int scenario)
	{
		boolean enoughCoin = scenario != 0;
		boolean taxed = scenario == 2;
		long debit = taxed ? 13 : 10;
		var transport = new java.util.concurrent.atomic.AtomicReference<PurchaseTransport>();
		var player = BridgeTestPlayers.player();
		int packetCap = com.l2jfree.Config.MAX_ITEM_IN_PACKET;
		com.l2jfree.Config.MAX_ITEM_IN_PACKET = 1;
		try
		{
		var client = mock(L2Client.class);
		var inventory = mock(PlayerInventory.class);
		var template = mock(L2Item.class);
		var list = mock(L2TradeList.class);
		var merchant = taxed ? mock(com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance.class) : null;
		if (taxed)
		{
			when(player.getTarget(com.l2jfree.gameserver.gameobjects.L2Merchant.class)).thenReturn(merchant);
			when(merchant.getIsInTown()).thenReturn(true);
			when(merchant.getTemplate()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate.class));
		}
		when(client.isBridge()).thenReturn(true);
		when(client.getActiveChar()).thenReturn(player);
		var facts = new ArrayList<java.util.List<com.l2jfree.gameserver.bridge.wire.Envelope>>();
		var sink = new BridgeSink(facts::add);
		sink.attach(client);
		org.mockito.Mockito.doAnswer(call ->
		{
			com.l2jfree.gameserver.network.packets.L2ServerPacket packet = call.getArgument(0);
			packet.prepareToSend(client, player);
			sink.deliver(packet, player);
			return null;
		}).when(client).sendPacket(any(com.l2jfree.gameserver.network.packets.L2ServerPacket.class));
		when(player.isGM()).thenReturn(true);
		when(player.getAccessLevel()).thenReturn(-1);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.validateWeight(anyInt())).thenReturn(true);
		when(inventory.validateCapacity(anyInt())).thenReturn(true);
		when(inventory.getItems()).thenReturn(new L2ItemInstance[0]);
		when(template.isStackable()).thenReturn(true);
		when(list.getListId()).thenReturn(1);
		when(list.containsItemId(1060)).thenReturn(true);
		when(list.getPriceForItemId(1060)).thenReturn(5L);
		when(player.reduceAdena(eq("Buy"), eq(debit), any(), eq(false))).thenAnswer(call ->
		{
			if (enoughCoin)
				EffectLedger.record(player, BridgeEffect.coin(-debit));
			if (scenario == 4)
				transport.get().lose();
			return enoughCoin;
		});
		when(inventory.addItem(eq("Buy"), eq(1060), eq(2L), eq(player), any())).thenAnswer(call ->
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
			if (scenario == 6)
				transport.get().overflow();
			return null;
		});
		try (var itemTables = mockStatic(ItemTable.class);
				var tradeTables = mockStatic(TradeListTable.class);
				var castleRates = mockStatic(com.l2jfree.gameserver.gameobjects.NpcCastle.class);
				var htmlCache = mockStatic(com.l2jfree.gameserver.cache.HtmCache.class);
				var tickets = mockStatic(MercTicketManager.class))
		{
			var items = mock(ItemTable.class);
			var trades = mock(TradeListTable.class);
			var mercs = mock(MercTicketManager.class);
			itemTables.when(ItemTable::getInstance).thenReturn(items);
			tradeTables.when(TradeListTable::getInstance).thenReturn(trades);
			tickets.when(MercTicketManager::getInstance).thenReturn(mercs);
			castleRates.when(() -> com.l2jfree.gameserver.gameobjects.NpcCastle.shopRate(null)).thenReturn(1.37);
			htmlCache.when(com.l2jfree.gameserver.cache.HtmCache::getInstance)
					.thenReturn(mock(com.l2jfree.gameserver.cache.HtmCache.class));
			when(items.getTemplate(1060)).thenReturn(template);
			when(trades.getBuyList(1)).thenReturn(list);
			when(mercs.getItemIds()).thenReturn(new int[0]);
			ArrayDeque<Runnable> fifo = new ArrayDeque<>();
			ArrayList<byte[]> output = new ArrayList<>();
			BridgeAttemptSession session = new BridgeAttemptSession(fifo::add, output::add);
			session.ready();
			PurchaseTransport recovery = scenario >= 3 ? new PurchaseTransport(client, player, fifo, scenario) : null;
			try
			{
			if (recovery != null)
			{
				transport.set(recovery);
				session = recovery.owner.attempts;
			}
			java.util.function.Supplier<byte[]> execution = () ->
			{
				var attempt = BridgeExecution.run(client, RequestBuyItem.forBridge(1, 1060, 2));
				assertThat(attempt.isFailed()).isFalse();
				return BridgeResults.encode(1, 7, BridgeOutcome.match(attempt, 7, 1060, 0, 2), 0, attempt);
			};
			session.accept(1, new byte[] { 7, 2 }, execution);
			session.accept(1, new byte[] { 7, 2 }, execution);
			if (scenario == 3)
				recovery.lose();
			fifo.remove().run();
			if (scenario == 5)
				recovery.lose();
			com.l2jfree.gameserver.bridge.wire.Envelope result;
			if (recovery != null)
			{
				result = recovery.resumeAndRead();
				byte[] expected = com.l2jfree.gameserver.bridge.wire.Codec.bridge().encode(java.util.List.of(result));
				session.accept(1, new byte[] { 7, 2 }, execution);
				assertThat(recovery.readResults().get(0)).containsExactly(expected);
			}
			else
			{
				session.accept(1, new byte[] { 7, 2 }, execution);
				assertThat(output).hasSize(2);
				assertThat(output.get(1)).containsExactly(output.get(0));
				result = com.l2jfree.gameserver.bridge.wire.Codec.bridge().decode(output.get(0)).get(0);
			}
			assertThat(fifo).isEmpty();
			assertThat(result.field(2).value().u()).isEqualTo(enoughCoin && scenario != 3 ? 2 : 3);
			if (scenario == 3)
				assertThat(result.field(65).value().u()).isEqualTo(9);
			if (!enoughCoin)
			{
				assertThat(result.field(3).value().u()).isEqualTo(16);
				assertThat(result.field(65).value().u()).isEqualTo(1);
				assertThat(result.field(66)).isNull();
			}
			assertThat(EffectLedger.isActive()).isFalse();
			org.mockito.Mockito.verify(player, org.mockito.Mockito.times(scenario == 3 ? 0 : 1))
					.reduceAdena("Buy", debit, merchant, false);
			org.mockito.Mockito.verify(inventory, org.mockito.Mockito.times(enoughCoin && scenario != 3 ? 1 : 0))
					.addItem("Buy", 1060, 2L, player, merchant);
			}
			finally { if (recovery != null) recovery.close(); }
		}
		}
		finally
		{
			com.l2jfree.Config.MAX_ITEM_IN_PACKET = packetCap;
		}
	}

	/** Socket branches and the session's real recovery path, with the same caller-thread FIFO as the handler fixture. */
	private static final class PurchaseTransport implements AutoCloseable
	{
		final org.mockito.MockedStatic<com.l2jfree.gameserver.ThreadPoolManager> pools;
		final BridgeLoopback loop;
		final BridgeLoopback.Transport first = new BridgeLoopback.Transport(new byte[32]);
		final BridgeLoopback.Session owner;
		final L2Client client;
		final ArrayDeque<Runnable> fifo;
		BridgeLoopback.Transport current;
		final long id;

		PurchaseTransport(L2Client client, com.l2jfree.gameserver.gameobjects.L2Player player,
				ArrayDeque<Runnable> fifo, int scenario)
		{
			this.client = client;
			this.fifo = fifo;
			pools = mockStatic(com.l2jfree.gameserver.ThreadPoolManager.class);
			pools.when(com.l2jfree.gameserver.ThreadPoolManager::getInstance)
					.thenReturn(mock(com.l2jfree.gameserver.ThreadPoolManager.class));
			var settings = new java.util.Properties();
			settings.setProperty("bridge.enabled", "true");
			settings.setProperty("bridge.port", Integer.toString(19970 + scenario));
			settings.setProperty("bridge.secret", "44".repeat(32));
			loop = BridgeLoopback.open(BridgeSettings.parse(settings), null);
			owner = loop.new Session(first);
			owner.sink.attach(client);
			org.mockito.Mockito.doAnswer(call -> { fifo.add(call.getArgument(0)); return null; })
					.when(client).submit(any(Runnable.class));
			owner.attach(client);
			id = BridgeAdmission.newSession(client);
			owner.id = id;
			owner.recovery.finishCheckpoint(owner.recovery.checkpoint());
			first.opened = true;
			owner.attempts.ready(first.attemptFence);
			when(player.getObjectId()).thenReturn(70);
			when(player.getView()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.view.PlayerView.class));
			when(player.getView().getObjectId()).thenReturn(70);
			when(player.getView().getMovementSpeedMultiplier()).thenReturn(1.0f);
			when(player.getAppearance()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance.class));
			when(player.getAppearance().getVisibleName()).thenReturn("Visible player");
			when(player.getStat()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.stat.PlayerStat.class));
			when(player.getStatus()).thenReturn(mock(com.l2jfree.gameserver.gameobjects.status.PlayerStatus.class));
			when(player.getSortedAllSkills(org.mockito.ArgumentMatchers.anyBoolean()))
					.thenReturn(new com.l2jfree.gameserver.model.skills.L2Skill[0]);
			org.mockito.Mockito.doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return true; })
					.when(player).captureBridgeScene(any(Runnable.class));
			org.mockito.Mockito.doAnswer(call ->
			{
				com.l2jfree.gameserver.network.packets.L2ServerPacket packet = call.getArgument(0);
				packet.prepareToSend(client, player);
				owner.sink.deliver(packet, player);
				return null;
			}).when(client).sendPacket(any(com.l2jfree.gameserver.network.packets.L2ServerPacket.class));
			owner.sink.deliver(new com.l2jfree.gameserver.network.packets.server.UserInfo(player), player);
			owner.sink.deliver(new com.l2jfree.gameserver.network.packets.server.ItemList(new L2ItemInstance[0], false), player);
			owner.sink.deliver(new com.l2jfree.gameserver.network.packets.server.SkillList(player), player);
			owner.sink.deliver(new com.l2jfree.gameserver.network.packets.server.QuestList(player), player);
			first.queued.clear();
			first.held = 0;
		}

		void lose()
		{
			try
			{
				var socket = mock(java.nio.channels.SocketChannel.class);
				when(socket.read(any(java.nio.ByteBuffer.class))).thenReturn(-1);
				loop.account();
				loop.serve(key(socket, first, true));
				org.mockito.Mockito.verify(socket).close();
				org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).requestBridgeDisconnect();
			}
			catch (java.io.IOException failure) { throw new AssertionError(failure); }
		}

		void overflow()
		{
			try
			{
				BridgeLoopback.offer(first, new byte[BridgeLoopback.OUT_QUEUE_BYTES]);
				BridgeLoopback.offer(first, new byte[1]);
				var socket = mock(java.nio.channels.SocketChannel.class);
				when(socket.write(any(java.nio.ByteBuffer.class))).thenAnswer(call ->
				{
					java.nio.ByteBuffer buffer = call.getArgument(0);
					int n = buffer.remaining(); buffer.position(buffer.limit()); return n;
				});
				loop.account();
				loop.serve(key(socket, first, false));
				org.mockito.Mockito.verify(socket).close();
				org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).requestBridgeDisconnect();
			}
			catch (java.io.IOException failure) { throw new AssertionError(failure); }
		}

		com.l2jfree.gameserver.bridge.wire.Envelope resumeAndRead()
		{
			current = new BridgeLoopback.Transport(new byte[32]);
			try
			{
				loop.acceptFrame(current, java.util.List.of(com.l2jfree.gameserver.bridge.wire.Envelope.of(
						com.l2jfree.gameserver.bridge.wire.Schemas.RESUME, java.util.List.of(
						com.l2jfree.gameserver.bridge.wire.Field.u(1, true, id),
						com.l2jfree.gameserver.bridge.wire.Field.u(2, true, 2),
						com.l2jfree.gameserver.bridge.wire.Field.data(3, true, new byte[32]),
						com.l2jfree.gameserver.bridge.wire.Field.data(4, true, com.l2jfree.gameserver.bridge.wire.Codec.profileName())))));
				while (!fifo.isEmpty()) fifo.remove().run();
				var records = read();
				int end = -1;
				int receipt = -1;
				for (int i = 0; i < records.size(); i++)
				{
					if (records.get(i).type() == com.l2jfree.gameserver.bridge.wire.Schemas.RESULT) receipt = i;
					if (records.get(i).type() == com.l2jfree.gameserver.bridge.wire.Schemas.CHECKPOINT
							&& records.get(i).field(1).value().u() == com.l2jfree.gameserver.bridge.wire.Schemas.END) end = i;
				}
				assertThat(receipt).isGreaterThan(end);
				assertThat(records.get(end).field(3).value().u()).isEqualTo(1);
				assertThat(records.get(records.size() - 1).type()).isEqualTo(com.l2jfree.gameserver.bridge.wire.Schemas.OPENED);
				return records.get(receipt);
			}
			catch (java.io.IOException failure) { throw new AssertionError(failure); }
		}

		java.util.List<byte[]> readResults()
		{
			return read().stream().filter(record -> record.type() == com.l2jfree.gameserver.bridge.wire.Schemas.RESULT)
					.map(record -> com.l2jfree.gameserver.bridge.wire.Codec.bridge().encode(java.util.List.of(record))).toList();
		}

		java.util.List<com.l2jfree.gameserver.bridge.wire.Envelope> read()
		{
			try
			{
				var bytes = new java.io.ByteArrayOutputStream();
				var socket = mock(java.nio.channels.SocketChannel.class);
				when(socket.write(any(java.nio.ByteBuffer.class))).thenAnswer(call ->
				{
					java.nio.ByteBuffer buffer = call.getArgument(0);
					byte[] copy = new byte[buffer.remaining()]; buffer.get(copy); bytes.write(copy); return copy.length;
				});
				loop.serve(key(socket, current, false));
				while (!fifo.isEmpty()) fifo.remove().run();
				loop.serve(key(socket, current, false));
				var input = new java.io.ByteArrayInputStream(bytes.toByteArray());
				var records = new ArrayList<com.l2jfree.gameserver.bridge.wire.Envelope>();
				while (input.available() != 0) records.addAll(com.l2jfree.gameserver.bridge.wire.Codec.bridge().read(input));
				return records;
			}
			catch (java.io.IOException failure) { throw new AssertionError(failure); }
		}

		private static java.nio.channels.SelectionKey key(java.nio.channels.SocketChannel socket,
				BridgeLoopback.Transport transport, boolean read)
		{
			var key = mock(java.nio.channels.SelectionKey.class);
			when(key.isValid()).thenReturn(true);
			when(key.isReadable()).thenReturn(read);
			when(key.channel()).thenReturn(socket);
			when(key.attachment()).thenReturn(transport);
			return key;
		}

		@Override
		public void close()
		{
			when(client.isBridgeDisconnectFinished()).thenReturn(true);
			loop.close();
			BridgeAdmission.reapSessions();
			pools.close();
		}
	}
}
