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
package com.l2jfree.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ItemCountDeltaTest
{
	@Test
	void actualDeltaIncludesClampingAndZeroInsteadOfRequestedCount()
	{
		L2ItemInstance item = mock(L2ItemInstance.class, CALLS_REAL_METHODS);
		doReturn(1060).when(item).getItemId();
		item.setCount(Integer.MAX_VALUE - 2L);
		assertThat(item.changeCountAndGetDelta(null, 10, null, null)).isEqualTo(2);
		assertThat(item.changeCountAndGetDelta(null, 10, null, null)).isZero();
		item.setCount(3);
		assertThat(item.changeCountAndGetDelta(null, -10, null, null)).isEqualTo(-3);
		assertThat(item.getCount()).isZero();
		assertThat(item.changeCountAndGetDelta(null, 0, null, null)).isZero();
		item.changeCount(null, 2, null, null);
		assertThat(item.getCount()).isEqualTo(2);
	}
}
