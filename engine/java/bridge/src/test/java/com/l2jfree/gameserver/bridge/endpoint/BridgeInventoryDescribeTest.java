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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;
import com.l2jfree.gameserver.model.items.ItemInfo;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.bridge.BridgeValue;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillList;
import com.l2jfree.gameserver.network.packets.server.BuyList;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.SellList;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

class BridgeInventoryDescribeTest
{
	private static L2ItemInstance item(int id, long count)
	{
		var template = mock(L2Item.class);
		when(template.getItemId()).thenReturn(id);
		when(template.getItemDisplayId()).thenReturn(id);
		var item = mock(L2ItemInstance.class);
		when(item.getItem()).thenReturn(template);
		when(item.getObjectId()).thenReturn(70);
		when(item.getItemId()).thenReturn(id);
		when(item.getItemDisplayId()).thenReturn(id);
		when(item.getCount()).thenReturn(count);
		return item;
	}

	@Test
	void inventoryDeltasUseItemInfoSnapshotsAndNeverReadAnAbsentPurse()
	{
		var item = item(1060, 5);
		var row = new ItemInfo(item, 2);
		when(item.getCount()).thenReturn(100L);
		var sink = new BridgeTestFacts();
		new InventoryUpdate(List.of(row)).describe(sink, BridgeTestPlayers.player());
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 23)).isNull();
		var goods = (BridgeValue.ListValue)BridgeTestFacts.field(sink.facts.get(0), 24).value();
		var fields = ((BridgeValue.ObjectValue)goods.items().get(0)).fields();
		assertThat(fields).contains(com.l2jfree.gameserver.network.bridge.BridgeField.number(4, 5),
				com.l2jfree.gameserver.network.bridge.BridgeField.number(7, 2));
		var removed = new ItemInfo(item(57, 9), 3);
		new InventoryUpdate(List.of(removed)).describe(sink, null);
		assertThat(BridgeTestFacts.field(sink.facts.get(1), 23).value()).isEqualTo(new BridgeValue.Number(0));
	}

	@Test
	void showWindowDoesNotChangeCompleteInventoryMeaning()
	{
		var items = new L2ItemInstance[] { item(57, 9) };
		var sink = new BridgeTestFacts();
		new ItemList(items, true).describe(sink, null);
		new ItemList(items, false).describe(sink, null);
		assertThat(sink.facts.get(0)).isEqualTo(sink.facts.get(1));
		assertThat(BridgeTestFacts.field(sink.facts.get(0), 31).value()).isEqualTo(new BridgeValue.Flag(true));
	}

	@Test
	void statusUpdatesContainOnlyTransmittedStatsAndRejectNegativeValues()
	{
		var sink = new BridgeTestFacts();
		var update = new StatusUpdate(70);
		update.addAttribute(StatusUpdate.CUR_HP, 3);
		update.addAttribute(StatusUpdate.MAX_LOAD, 100);
		update.describe(sink, null);
		var stats = (BridgeValue.ObjectValue)BridgeTestFacts.field(sink.facts.get(0), 25).value();
		assertThat(stats.fields()).containsExactly(com.l2jfree.gameserver.network.bridge.BridgeField.number(1, 3));
		update.addAttribute(StatusUpdate.SP, -1);
		assertThatIllegalArgumentException().isThrownBy(() -> update.describe(sink, null));
	}

	@Test
	void tradeAndPracticePanelsDoNotBecomeDialogueSpeech()
	{
		var sink = new BridgeTestFacts();
		var unlimited = item(1060, -1);
		new BuyList(List.of(unlimited), 1, 10).describe(sink, null);
		var player = BridgeTestPlayers.player();
		new SellList(player, mock(L2MerchantInstance.class)).describe(sink, player);
		var practice = new AcquireSkillList(AcquireSkillList.SkillType.Usual);
		practice.addSkill(12, 1, 2, 10, 0);
		practice.describe(sink, player);
		assertThat(sink.facts).extracting(fact -> fact.kind()).containsExactly(12, 13, 22);
		for (var fact : sink.facts)
			assertThat(BridgeTestFacts.field(fact, 68)).isNull();
		assertThat(((BridgeValue.ListValue)BridgeTestFacts.field(sink.facts.get(1), 24).value()).items()).isEmpty();
	}
}
