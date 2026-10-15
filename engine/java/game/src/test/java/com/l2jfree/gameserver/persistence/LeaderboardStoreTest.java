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
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.persistence.LeaderboardStore.Board;
import com.l2jfree.gameserver.persistence.LeaderboardStore.Entry;
import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class LeaderboardStoreTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@BeforeAll
	static void createPlayers() throws Exception
	{
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			for (int id = 1; id <= 3; id++)
				statement.execute("INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id) "
						+ "VALUES (" + id + ", 'test', 'Player" + id + "', 0, 0, 0)");
		}
	}
	
	@Test
	void eachBoardKeepsItsOwnEntriesInTheirOrder() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(LeaderboardStore.load(con, Board.ARENA)).isEmpty();
			
			LeaderboardStore.replace(con, Board.ARENA, List.of(new Entry(2, "Player2", 5, 1), new Entry(1, "Player1", 3, 3)));
			LeaderboardStore.replace(con, Board.FISHING, List.of(new Entry(1, "Player1", 7, 2)));
			assertThat(LeaderboardStore.load(con, Board.ARENA)).containsExactly(new Entry(2, "Player2", 5, 1),
					new Entry(1, "Player1", 3, 3));
			assertThat(LeaderboardStore.load(con, Board.FISHING)).containsExactly(new Entry(1, "Player1", 7, 2));
			
			LeaderboardStore.replace(con, Board.ARENA, List.of(new Entry(3, "Player3", 1, 0)));
			assertThat(LeaderboardStore.load(con, Board.ARENA)).containsExactly(new Entry(3, "Player3", 1, 0));
			assertThat(LeaderboardStore.load(con, Board.FISHING)).hasSize(1);
		}
	}
	
	@Test
	void entriesOfDeletedPlayersAreDroppedNotFailed() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			LeaderboardStore.replace(con, Board.FISHING, List.of(new Entry(99, "Gone", 1, 1), new Entry(2, "Player2", 4, 0)));
			assertThat(LeaderboardStore.load(con, Board.FISHING)).containsExactly(new Entry(2, "Player2", 4, 0));
		}
	}
}
