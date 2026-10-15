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
package com.l2jfree.gameserver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.ItemRequest;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;

class TradeListPrivateStoreCapacityTest
{
	@Test
	@DisplayName("private-store weight uses the seller's item when the request has no item ID")
	void weightUsesActualSellerItem()
	{
		ItemRequest request = new ItemRequest(42, 2, 10);
		L2ItemInstance sellerItem = mock(L2ItemInstance.class);
		L2Item template = mock(L2Item.class);
		when(sellerItem.getItem()).thenReturn(template);
		when(template.getWeight()).thenReturn(100);

		assertThat(request.getItemId()).isZero();
		assertThat(TradeList.addItemWeight(0, sellerItem, request.getCount())).isEqualTo(200);
	}

	@Test
	@DisplayName("private-store slots use the seller's item ID when the request has no item ID")
	void slotsUseActualSellerItem()
	{
		ItemRequest request = new ItemRequest(42, 1, 10);
		L2ItemInstance sellerItem = mock(L2ItemInstance.class);
		L2Item template = mock(L2Item.class);
		PlayerInventory buyerInventory = mock(PlayerInventory.class);
		when(sellerItem.getItem()).thenReturn(template);
		when(sellerItem.getItemId()).thenReturn(5000);
		when(template.isStackable()).thenReturn(true);

		assertThat(request.getItemId()).isZero();
		assertThat(TradeList.addItemSlots(0, sellerItem, request.getCount(), buyerInventory)).isEqualTo(1);
		verify(buyerInventory).getItemByItemId(5000);
	}

	@Test
	@DisplayName("large requested quantities cannot wrap capacity requirements")
	void capacityRequirementCannotWrap()
	{
		assertThat(TradeList.addCapacityRequirement(0, 2, 100)).isEqualTo(200);
		assertThat(TradeList.addCapacityRequirement(0, Long.MAX_VALUE, 100)).isEqualTo(-1);
		assertThat(TradeList.addCapacityRequirement(Integer.MAX_VALUE, 1, 1)).isEqualTo(-1);
	}
}
