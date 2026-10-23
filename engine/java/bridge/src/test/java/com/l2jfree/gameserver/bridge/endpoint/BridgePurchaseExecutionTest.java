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
	@ValueSource(booleans = { true, false })
	void liveDuplicateRunsTheActualPurchaseHandlerOnce(boolean enoughCoin)
	{
		var player = BridgeTestPlayers.player();
		int packetCap = com.l2jfree.Config.MAX_ITEM_IN_PACKET;
		com.l2jfree.Config.MAX_ITEM_IN_PACKET = 1;
		try
		{
		var client = mock(L2Client.class);
		var inventory = mock(PlayerInventory.class);
		var template = mock(L2Item.class);
		var list = mock(L2TradeList.class);
		when(client.isBridge()).thenReturn(true);
		when(client.getActiveChar()).thenReturn(player);
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
		when(player.reduceAdena(eq("Buy"), eq(10L), any(), eq(false))).thenAnswer(call ->
		{
			if (enoughCoin)
				EffectLedger.record(player, BridgeEffect.coin(-10));
			return enoughCoin;
		});
		when(inventory.addItem(eq("Buy"), eq(1060), eq(2L), eq(player), any())).thenAnswer(call ->
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, 2));
			return null;
		});
		try (var itemTables = mockStatic(ItemTable.class);
				var tradeTables = mockStatic(TradeListTable.class);
				var tickets = mockStatic(MercTicketManager.class))
		{
			var items = mock(ItemTable.class);
			var trades = mock(TradeListTable.class);
			var mercs = mock(MercTicketManager.class);
			itemTables.when(ItemTable::getInstance).thenReturn(items);
			tradeTables.when(TradeListTable::getInstance).thenReturn(trades);
			tickets.when(MercTicketManager::getInstance).thenReturn(mercs);
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
			assertThat(result.field(2).value().u()).isEqualTo(enoughCoin ? 2 : 4);
			org.mockito.Mockito.verify(player).reduceAdena("Buy", 10L, null, false);
			org.mockito.Mockito.verify(inventory, org.mockito.Mockito.times(enoughCoin ? 1 : 0))
					.addItem("Buy", 1060, 2L, player, null);
		}
		}
		finally
		{
			com.l2jfree.Config.MAX_ITEM_IN_PACKET = packetCap;
		}
	}
}
