/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

class MultiSellIngredientReservationTest
{
	@Test
	void reservesUnequippedInstanceWhenFirstCopyIsEquipped()
	{
		L2ItemInstance equipped = item(100, 1, 0, false);
		L2ItemInstance available = item(101, 1, 0, false);
		when(equipped.isEquipped()).thenReturn(true);

		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(
				new L2ItemInstance[] { equipped, available });

		assertThat(plan.reserve(1, -1, 1)).isTrue();
		assertThat(plan.debits()).hasSize(1);
		assertThat(plan.debits().get(0).item()).isSameAs(available);
	}

	@Test
	void repeatedLinesCannotConsumeTheSameInstanceTwice()
	{
		L2ItemInstance single = item(100, 1, 0, false);
		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(new L2ItemInstance[] { single });

		assertThat(plan.reserve(1, -1, 1)).isTrue();
		assertThat(plan.reserve(1, -1, 1)).isFalse();
		assertThat(plan.debits()).hasSize(1);
	}

	@Test
	void failedReservationDoesNotConsumeAnyAvailableBalance()
	{
		L2ItemInstance first = item(100, 1, 0, false);
		L2ItemInstance second = item(101, 1, 0, false);
		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(
				new L2ItemInstance[] { first, second });

		assertThat(plan.reserve(1, -1, 3)).isFalse();
		assertThat(plan.reserve(1, -1, 2)).isTrue();
		assertThat(plan.debits()).hasSize(2);
	}

	@Test
	void splitsStackCountAcrossInstances()
	{
		L2ItemInstance first = item(100, 1, 0, true);
		L2ItemInstance second = item(101, 1, 0, true);
		when(first.getCount()).thenReturn(2L);
		when(second.getCount()).thenReturn(3L);
		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(
				new L2ItemInstance[] { first, second });

		assertThat(plan.reserve(1, -1, 4)).isTrue();
		assertThat(plan.debits()).hasSize(2);
		assertThat(plan.debits().get(0).count()).isEqualTo(2);
		assertThat(plan.debits().get(1).count()).isEqualTo(2);
	}

	@Test
	void exactRetainedIngredientAlsoSatisfiesAnyEnchantmentRequirement()
	{
		L2ItemInstance plain = item(100, 1, 0, false);
		L2ItemInstance enchanted = item(101, 1, 5, false);
		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(
				new L2ItemInstance[] { plain, enchanted });

		assertThat(plan.reserveRetained(Arrays.asList(
				new MultiSellIngredientReservation.Requirement(1, 0, 1),
				new MultiSellIngredientReservation.Requirement(1, -1, 1)))).isTrue();
		assertThat(plan.reserve(1, -1, 1)).isTrue();
		assertThat(plan.debits().get(0).item()).isSameAs(enchanted);
	}

	@Test
	void failedRetainedReservationRestoresAllCandidateBalances()
	{
		L2ItemInstance plain = item(100, 1, 0, false);
		MultiSellIngredientReservation plan = new MultiSellIngredientReservation(new L2ItemInstance[] { plain });

		assertThat(plan.reserveRetained(Arrays.asList(
				new MultiSellIngredientReservation.Requirement(1, 0, 1),
				new MultiSellIngredientReservation.Requirement(1, 5, 1)))).isFalse();
		assertThat(plan.reserve(1, -1, 1)).isTrue();
	}

	private static L2ItemInstance item(int objectId, int itemId, int enchant, boolean stackable)
	{
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getObjectId()).thenReturn(objectId);
		when(item.getItemId()).thenReturn(itemId);
		when(item.getEnchantLevel()).thenReturn(enchant);
		when(item.getCount()).thenReturn(1L);
		when(item.isStackable()).thenReturn(stackable);
		return item;
	}
}
