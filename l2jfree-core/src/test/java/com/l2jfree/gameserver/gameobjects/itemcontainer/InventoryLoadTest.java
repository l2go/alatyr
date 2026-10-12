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
package com.l2jfree.gameserver.gameobjects.itemcontainer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryLoadTest
{
	@Test
	@DisplayName("a stack whose product does not fit in 32 bits keeps the full product")
	void arrowStackKeepsTheFullProduct()
	{
		assertThat(Inventory.addLoad(0, 6, 400_000_000L)).isEqualTo(2_400_000_000L);
	}

	@Test
	@DisplayName("a product that does not fit in a signed long is refused")
	void overflowingProductIsRefused()
	{
		assertThat(Inventory.addLoad(0, 6, Long.MAX_VALUE)).isEqualTo(-1);
		assertThat(Inventory.addLoad(Long.MAX_VALUE - 5, 6, 1)).isEqualTo(-1);
		assertThat(Inventory.addLoad(-1, 6, 1)).isEqualTo(-1);
	}

	@Test
	@DisplayName("a fitting product is added, and a status packet saturates above 32 bits")
	void fittingProductAndStatusPacket()
	{
		assertThat(Inventory.addLoad(100, 0, Long.MAX_VALUE)).isEqualTo(100);
		assertThat(Inventory.addLoad(10, 5, 3)).isEqualTo(25);
		assertThat(Inventory.toStatusLoad(2_400_000_000L)).isEqualTo(Integer.MAX_VALUE);
		assertThat(Inventory.toStatusLoad(25)).isEqualTo(25);
		assertThat(Inventory.toStatusLoad(0)).isZero();
	}
}
