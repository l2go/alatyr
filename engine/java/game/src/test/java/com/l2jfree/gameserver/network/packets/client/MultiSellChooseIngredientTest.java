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
package com.l2jfree.gameserver.network.packets.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.model.items.L2ItemInstance;

class MultiSellChooseIngredientTest
{
	@Test
	@DisplayName("an equipped first copy is skipped when an unequipped copy is available")
	void skipsEquippedFirstCopy()
	{
		L2ItemInstance equipped = item(5);
		L2ItemInstance available = item(6);
		when(equipped.isEquipped()).thenReturn(true);

		L2ItemInstance[] eligible = MultiSellChoose.eligibleIngredients(
				new L2ItemInstance[] { equipped, available }, -1);

		assertThat(eligible).containsExactly(available);
		assertThat(MultiSellChoose.hasRequiredIngredientCount(eligible, 1)).isTrue();
	}

	@Test
	@DisplayName("worn copies are excluded before any ingredient is consumed")
	void rejectsOnlyWornCopy()
	{
		L2ItemInstance worn = item(0);
		when(worn.isWear()).thenReturn(true);

		L2ItemInstance[] eligible = MultiSellChoose.eligibleIngredients(new L2ItemInstance[] { worn }, -1);

		assertThat(eligible).isEmpty();
		assertThat(MultiSellChoose.hasRequiredIngredientCount(eligible, 1)).isFalse();
	}

	@Test
	@DisplayName("non-stackable copies are selected by lowest enchantment")
	void choosesLowestEligibleEnchantment()
	{
		L2ItemInstance high = item(8);
		L2ItemInstance low = item(2);
		L2ItemInstance[] eligible = MultiSellChoose.eligibleIngredients(
				new L2ItemInstance[] { high, low }, -1);

		assertThat(eligible).containsExactly(low, high);
		assertThat(MultiSellChoose.hasRequiredIngredientCount(eligible, 2)).isTrue();
		assertThat(MultiSellChoose.hasRequiredIngredientCount(eligible, 3)).isFalse();
	}

	@Test
	@DisplayName("maintained enchantment selects only exact eligible copies")
	void selectsExactEnchantment()
	{
		L2ItemInstance other = item(2);
		L2ItemInstance matching = item(5);

		assertThat(MultiSellChoose.eligibleIngredients(new L2ItemInstance[] { other, matching }, 5))
				.containsExactly(matching);
	}

	@Test
	@DisplayName("available stack counts are combined without overflowing")
	void combinesEligibleStacks()
	{
		L2ItemInstance first = item(0);
		L2ItemInstance second = item(0);
		when(first.isStackable()).thenReturn(true);
		when(second.isStackable()).thenReturn(true);
		when(first.getCount()).thenReturn(Long.MAX_VALUE - 1);
		when(second.getCount()).thenReturn(2L);

		assertThat(MultiSellChoose.hasRequiredIngredientCount(new L2ItemInstance[] { first, second },
				Long.MAX_VALUE)).isTrue();
	}

	@Test
	@DisplayName("temporary wear copies do not count as a persistent reward stack")
	void ignoresWearStackForProductCapacity()
	{
		PlayerInventory inventory = mock(PlayerInventory.class);
		L2ItemInstance worn = item(0);
		when(worn.isStackable()).thenReturn(true);
		when(worn.isWear()).thenReturn(true);
		when(inventory.getAllItemsByItemId(57)).thenReturn(new L2ItemInstance[] { worn });

		assertThat(MultiSellChoose.hasPersistentStack(inventory, 57)).isFalse();
	}

	private static L2ItemInstance item(int enchantment)
	{
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getEnchantLevel()).thenReturn(enchantment);
		return item;
	}
}
