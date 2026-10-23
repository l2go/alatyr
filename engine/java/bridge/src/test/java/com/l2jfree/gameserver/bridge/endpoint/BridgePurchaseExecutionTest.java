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
		boolean enoughCoin = scenario != 0;
		boolean taxed = scenario == 2;
		long debit = taxed ? 13 : 10;
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
			return enoughCoin;
		});
		when(inventory.addItem(eq("Buy"), eq(1060), eq(2L), eq(player), any())).thenAnswer(call ->
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
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
			java.util.function.Supplier<byte[]> execution = () ->
			{
				var attempt = BridgeExecution.run(client, RequestBuyItem.forBridge(1, 1060, 2));
				assertThat(attempt.isFailed()).isFalse();
				return BridgeResults.encode(1, 7, BridgeOutcome.match(attempt, 7, 1060, 0, 2), 0, attempt);
			};
			session.accept(1, new byte[] { 7, 2 }, execution);
			session.accept(1, new byte[] { 7, 2 }, execution);
			fifo.remove().run();
			session.accept(1, new byte[] { 7, 2 }, execution);
			assertThat(fifo).isEmpty();
			assertThat(output).hasSize(2);
			assertThat(output.get(1)).containsExactly(output.get(0));
			var result = com.l2jfree.gameserver.bridge.wire.Codec.bridge().decode(output.get(0)).get(0);
			assertThat(result.field(2).value().u()).isEqualTo(enoughCoin ? 2 : 3);
			if (!enoughCoin)
			{
				assertThat(result.field(3).value().u()).isEqualTo(16);
				assertThat(result.field(65).value().u()).isEqualTo(1);
				assertThat(result.field(66)).isNull();
			}
			assertThat(EffectLedger.isActive()).isFalse();
			org.mockito.Mockito.verify(player).reduceAdena("Buy", debit, merchant, false);
			org.mockito.Mockito.verify(inventory, org.mockito.Mockito.times(enoughCoin ? 1 : 0))
					.addItem("Buy", 1060, 2L, player, merchant);
		}
		}
		finally
		{
			com.l2jfree.Config.MAX_ITEM_IN_PACKET = packetCap;
		}
	}
}
