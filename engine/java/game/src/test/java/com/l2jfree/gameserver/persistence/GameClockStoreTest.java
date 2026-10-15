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
import java.sql.Statement;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class GameClockStoreTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@BeforeAll
	static void migrate()
	{
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
	}
	
	@BeforeEach
	void clear() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			statement.execute("DELETE FROM game_clock");
		}
	}
	
	@Test
	void aNewWorldHasNoSavedClock() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(GameClockStore.load(con)).isEmpty();
		}
	}
	
	@Test
	void keepsTheLastSavedGameTime() throws Exception
	{
		// The first game date (GameTimeManager): 5 June 1281, 23:45, a Julian date before 1970.
		long first = new GregorianCalendar(1281, 5, 5, 23, 45).getTimeInMillis();
		long later = new GregorianCalendar(1300, 1, 29, 6, 0).getTimeInMillis();
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			GameClockStore.save(con, first);
			assertThat(GameClockStore.load(con)).hasValue(first);
			GameClockStore.save(con, later);
			assertThat(GameClockStore.load(con)).hasValue(later);
		}
	}
}
