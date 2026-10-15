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
package com.l2jfree.gameserver.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.persistence.AdminBookmarkStore.Bookmark;
import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class AdminBookmarkStoreTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@BeforeAll
	static void migrate()
	{
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
	}
	
	@Test
	void bookmarksKeepTheirOrderAndAReusedNameMovesTheBookmark() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(AdminBookmarkStore.load(con)).isEmpty();
			
			AdminBookmarkStore.save(con, new Bookmark("giran", 83400, 147943, -3404));
			AdminBookmarkStore.save(con, new Bookmark("aden", 147450, 26741, -2204));
			AdminBookmarkStore.save(con, new Bookmark("giran", 1, 2, 3));
			assertThat(AdminBookmarkStore.load(con)).containsExactly(new Bookmark("aden", 147450, 26741, -2204),
					new Bookmark("giran", 1, 2, 3));
			
			AdminBookmarkStore.delete(con, "giran");
			AdminBookmarkStore.delete(con, "unknown");
			assertThat(AdminBookmarkStore.load(con)).containsExactly(new Bookmark("aden", 147450, 26741, -2204));
		}
	}
}
