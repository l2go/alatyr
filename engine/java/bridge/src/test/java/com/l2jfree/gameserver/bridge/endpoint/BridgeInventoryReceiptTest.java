package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.bridge.Attempt;
import com.l2jfree.gameserver.network.bridge.EffectLedger;

class BridgeInventoryReceiptTest
{
	@Test
	void centralStackMutationsRecordActualOwnerDeltasWithoutWrapperDuplicates()
	{
		var player = BridgeTestPlayers.player();
		L2ItemInstance item = mock(L2ItemInstance.class, CALLS_REAL_METHODS);
		doReturn(1060).when(item).getItemId();
		doReturn(true).when(item).isStackable();
		doNothing().when(item).updateDatabase();
		item.setCount(Integer.MAX_VALUE - 2L);
		var inventory = new PlayerInventory(player)
		{
			{
				addItem(item);
			}
			@Override
			public void refreshWeight() { }
		};
		Attempt attempt = new Attempt(player);
		EffectLedger.open(attempt);
		try
		{
			inventory.addItem(null, 1060, 10, player, null);
			inventory.addItem(null, 1060, 10, player, null);
			assertThat(attempt.effects()).hasSize(1);
			assertThat(attempt.effects().get(0).delta()).isEqualTo(2);
			inventory.destroyItem("fixture", item, 1, player, null);
			assertThat(attempt.effects()).hasSize(2);
			assertThat(attempt.effects().get(1).delta()).isEqualTo(-1);
			assertThat(attempt.isLedgerIncomplete()).isFalse();
		}
		finally
		{
			EffectLedger.close();
		}
	}
}
