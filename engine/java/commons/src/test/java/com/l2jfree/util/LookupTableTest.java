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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LookupTableTest
{
	@Test
	@DisplayName("Removing absent keys leaves mappings and backing storage unchanged")
	void removeAbsentKeyDoesNotChangeTable() throws Exception
	{
		final LookupTable<String> empty = new LookupTable<>();
		final Object[] emptyStorage = storageOf(empty);
		final Object[] emptyContents = emptyStorage.clone();

		assertNull(empty.remove(100));
		assertTrue(empty.isEmpty());
		assertEquals(0, empty.size());
		assertSame(emptyStorage, storageOf(empty));
		assertArrayEquals(emptyContents, storageOf(empty));

		final LookupTable<String> table = new LookupTable<>();
		table.put(10, "ten");
		table.put(12, "twelve");
		final Object[] storage = storageOf(table);
		final Object[] contents = storage.clone();

		assertNull(table.remove(11)); // An in-range gap.
		assertNull(table.remove(9)); // Below the backing array.
		assertNull(table.remove(100)); // Above the backing array.
		assertEquals(2, table.size());
		assertFalse(table.isEmpty());
		assertEquals("ten", table.get(10));
		assertEquals("twelve", table.get(12));
		assertSame(storage, storageOf(table));
		assertArrayEquals(contents, storageOf(table));

		assertEquals("ten", table.remove(10));
		assertEquals(1, table.size());
		assertEquals("twelve", table.remove(12));
		assertEquals(0, table.size());
		assertTrue(table.isEmpty());
	}

	private static Object[] storageOf(final LookupTable<?> table) throws Exception
	{
		final Field field = LookupTable.class.getDeclaredField("_array");
		field.setAccessible(true);
		return (Object[])field.get(table);
	}
}
