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
package com.l2jfree.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.Test;

class L2FastSetTest
{
	@Test
	void keepsInsertionOrderAndReturnsNullWhenEmpty()
	{
		L2FastSet<Integer> set = new L2FastSet<Integer>();
		assertThat(set.getFirst()).isNull();
		assertThat(set.removeFirst()).isNull();
		
		set.addAll(new Integer[] { 3, 1, 2, 1 });
		
		assertThat(set).containsExactly(3, 1, 2);
		assertThat(set.getFirst()).isEqualTo(3);
		assertThat(set.getLast()).isEqualTo(2);
		assertThat(set.removeFirst()).isEqualTo(3);
		assertThat(set.removeLast()).isEqualTo(2);
		assertThat(set).containsExactly(1);
	}
	
	@Test
	void sharedSetKeepsElementsAndToleratesChangesWhileIterating()
	{
		L2FastSet<Integer> set = new L2FastSet<Integer>(Set.of(1, 2)).setShared(true);
		assertThat(set.isShared()).isTrue();
		
		for (Iterator<Integer> it = set.iterator(); it.hasNext();)
			set.add(it.next() + 10);
		
		assertThat(set).contains(1, 2, 11, 12);
		assertThat(set.removeFirst()).isNotNull();
	}
}
