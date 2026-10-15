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

import java.util.List;

import org.junit.jupiter.api.Test;

class L2ArraysTest
{
	/** The list is documented to survive removal inside a foreach loop, also above eight elements. */
	@Test
	void foreachSafeListToleratesRemovalAtEverySize()
	{
		for (int size : new int[] { 3, 8, 9, 40 })
		{
			Integer[] values = new Integer[size];
			for (int i = 0; i < size; i++)
				values[i] = i;
			
			List<Integer> list = L2Arrays.asForeachSafeList(values);
			for (Integer value : list)
				if (value % 2 == 0)
					list.remove(value);
			
			assertThat(list).as("size " + size).hasSize(size / 2);
		}
	}
	
	@Test
	void foreachSafeListDropsNullsAndAllowsAddition()
	{
		List<String> list = L2Arrays.asForeachSafeList("a", null, "b");
		list.add("c");
		
		assertThat(list).containsExactly("a", "b", "c");
	}
}
