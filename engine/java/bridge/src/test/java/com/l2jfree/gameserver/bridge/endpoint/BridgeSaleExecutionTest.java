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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.bridge.BridgeEffect;
import com.l2jfree.gameserver.network.bridge.EffectLedger;
import com.l2jfree.gameserver.network.packets.client.RequestSellItem;

/** Exercises the existing sale handler; separate inventory tests prove mutation hook accounting. */
class BridgeSaleExecutionTest
{
	@ParameterizedTest
	@ValueSource(ints = { 0, 10 })
	void actualSaleHandlerUsesTheDestroyedQuantityAndActualCoinReceipt(int referencePrice)
	{
		var player = BridgeTestPlayers.player();
		int packetCap = com.l2jfree.Config.MAX_ITEM_IN_PACKET;
		com.l2jfree.Config.MAX_ITEM_IN_PACKET = 1;
		try
		{
		L2Client client = mock(L2Client.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(client.isBridge()).thenReturn(true);
		when(client.getActiveChar()).thenReturn(player);
		when(player.isGM()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.checkItemManipulation(70, 2, "sell")).thenReturn(item);
		when(item.isSellable()).thenReturn(true);
		when(item.getReferencePrice()).thenReturn(referencePrice);
		when(inventory.destroyItem(eq("Sell"), eq(70), eq(2L), eq(player), any())).thenAnswer(call ->
		{
			EffectLedger.record(player, BridgeEffect.item(1060, 70, -2));
			return item;
		});
		doAnswer(call ->
		{
			long paid = call.getArgument(1);
			if (paid != 0)
				EffectLedger.record(player, BridgeEffect.coin(paid));
			return null;
		}).when(player).addAdena(anyString(), anyLong(), any(), anyBoolean());
		var attempt = BridgeExecution.run(client, RequestSellItem.forBridge(0, 70, 1060, 2));
		assertThat(attempt.isFailed()).isFalse();
		assertThat(BridgeOutcome.match(attempt, 8, 1060, 70, 2))
				.isEqualTo(referencePrice == 0 ? BridgeOutcome.UNKNOWN : BridgeOutcome.DONE);
		assertThat(attempt.effects()).hasSize(referencePrice == 0 ? 1 : 2);
		if (referencePrice != 0)
			assertThat(attempt.effects().get(1).delta()).isEqualTo(10);
		}
		finally
		{
			com.l2jfree.Config.MAX_ITEM_IN_PACKET = packetCap;
		}
	}
}
