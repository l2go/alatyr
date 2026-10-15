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
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class AnnouncementStoreTest
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
	void aNewWorldHasTheShippedAnnouncementAndKeepsTheOrderOfEverySave() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(AnnouncementStore.load(con)).containsExactly("Write your announcements here!");
			
			AnnouncementStore.replaceAll(con, List.of("Welcome", "Siege at 20:00", "Welcome"));
			assertThat(AnnouncementStore.load(con)).containsExactly("Welcome", "Siege at 20:00", "Welcome");
			
			AnnouncementStore.replaceAll(con, List.of("Siege at 20:00"));
			assertThat(AnnouncementStore.load(con)).containsExactly("Siege at 20:00");
			
			AnnouncementStore.replaceAll(con, List.of());
			assertThat(AnnouncementStore.load(con)).isEmpty();
		}
	}
}
