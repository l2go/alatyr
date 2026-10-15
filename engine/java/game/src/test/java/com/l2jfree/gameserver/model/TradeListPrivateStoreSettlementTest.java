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
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.ItemRequest;

class TradeListPrivateStoreSettlementTest
{
	@Test
	@DisplayName("a repeated object ID is rejected before the buyer is charged")
	void repeatedObjectIdIsRejected()
	{
		ItemRequest[] lines = {
			new ItemRequest(42, 5, 10),
			new ItemRequest(42, 5, 10)
		};

		assertThat(TradeList.hasRepeatedObjectIds(lines)).isTrue();
	}

	@Test
	@DisplayName("distinct object IDs are accepted even when they share a price")
	void distinctObjectIdsAreAccepted()
	{
		ItemRequest[] lines = {
			new ItemRequest(42, 5, 10),
			new ItemRequest(43, 5, 10)
		};

		assertThat(TradeList.hasRepeatedObjectIds(lines)).isFalse();
	}

	@Test
	@DisplayName("only delivered items are paid for when a transfer stops")
	void partialTransferRefundsUndeliveredPrice()
	{
		PlayerInventory buyer = mock(PlayerInventory.class);
		PlayerInventory seller = mock(PlayerInventory.class);

		TradeList.settlePrivateStoreSale(buyer, seller, 100, 40, null, null);

		verify(buyer).addAdena("PrivateStore", 60, null, null);
		verify(seller).addAdena("PrivateStore", 40, null, null);
	}

	@Test
	@DisplayName("a failed first transfer refunds the entire purchase")
	void noTransferRefundsFullPrice()
	{
		PlayerInventory buyer = mock(PlayerInventory.class);
		PlayerInventory seller = mock(PlayerInventory.class);

		TradeList.settlePrivateStoreSale(buyer, seller, 100, 0, null, null);

		verify(buyer).addAdena("PrivateStore", 100, null, null);
		verifyNoInteractions(seller);
	}
}
