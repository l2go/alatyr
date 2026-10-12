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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

class L2TradeListStockTest
{
	@Test
	@DisplayName("two buyers cannot both take the last limited units")
	void lastUnitsGoToOneBuyer() throws Exception
	{
		AtomicLong stock = new AtomicLong(5);
		L2ItemInstance item = mock(L2ItemInstance.class);
		when(item.getItemId()).thenReturn(1829);
		when(item.getCount()).thenAnswer(invocation -> stock.get());
		doAnswer(invocation -> {
			stock.set(invocation.getArgument(0));
			return null;
		}).when(item).setCount(anyLong());
		
		L2TradeList list = new L2TradeList(1);
		list.addItem(item);
		
		Thread[] buyers = new Thread[2];
		boolean[] reserved = new boolean[2];
		for (int i = 0; i < buyers.length; i++)
		{
			int buyer = i;
			buyers[i] = new Thread(() -> reserved[buyer] = list.decreaseCount(1829, 5));
		}
		for (Thread buyer : buyers)
			buyer.start();
		for (Thread buyer : buyers)
			buyer.join();
		
		assertThat(reserved[0] ^ reserved[1]).isTrue();
		assertThat(stock.get()).isZero();
		list.increaseCount(1829, 5);
		assertThat(item.getCount()).isEqualTo(5);
	}
}
