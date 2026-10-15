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
package com.l2jfree;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/** Code that opens and closes its own connections runs as one transaction inside WorldTransaction.run. */
@Tag("integration")
class L2DatabaseFactoryTransactionTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	private static L2DatabaseFactory factory;
	
	@BeforeAll
	static void createTable() throws Exception
	{
		factory = new L2DatabaseFactory(
				L2DatabaseFactory.poolConfig(POSTGRES.jdbcUrl(), POSTGRES.user(), POSTGRES.password(), 3, 0));
		execute("CREATE TABLE public.probe (id integer PRIMARY KEY)");
	}
	
	@AfterAll
	static void shutdown() throws Exception
	{
		factory.shutdown();
	}
	
	@BeforeEach
	void clear()
	{
		execute("DELETE FROM public.probe");
	}
	
	@Test
	void everyStepCommitsTogether()
	{
		assertThat(WorldTransaction.run("probe", factory::getConnection, () -> {
			execute("INSERT INTO public.probe VALUES (1)");
			execute("INSERT INTO public.probe VALUES (2)");
		})).isTrue();
		assertThat(ids()).containsExactly(1, 2);
	}
	
	@Test
	void aStepThatFailsAndOnlyLogsRollsBackTheOthers()
	{
		assertThat(WorldTransaction.run("probe", factory::getConnection, () -> {
			execute("INSERT INTO public.probe VALUES (1)");
			execute("INSERT INTO public.probe VALUES (1)"); // fails; the step logs and carries on, as store steps do
			execute("INSERT INTO public.probe VALUES (2)");
		})).isFalse();
		assertThat(ids()).isEmpty();
	}
	
	@Test
	void aThrownExceptionRollsBack()
	{
		assertThat(WorldTransaction.run("probe", factory::getConnection, () -> {
			execute("INSERT INTO public.probe VALUES (1)");
			throw new IllegalStateException("expected by the test");
		})).isFalse();
		assertThat(ids()).isEmpty();
	}
	
	@Test
	void stepsShareOneConnectionAndCannotEndTheTransaction() throws Exception
	{
		List<Connection> seen = new ArrayList<>();
		assertThat(WorldTransaction.run("probe", factory::getConnection, () -> {
			try
			{
				for (int i = 1; i <= 2; i++)
				{
					Connection con = factory.getConnection();
					seen.add(con.unwrap(Connection.class));
					con.setAutoCommit(true);
					try (Statement statement = con.createStatement())
					{
						statement.execute("INSERT INTO public.probe VALUES (" + i + ")");
					}
					con.commit();
					con.close();
					assertThat(con.isClosed()).isFalse();
				}
				// Not committed yet: another connection sees nothing.
				assertThat(idsOutside()).isEmpty();
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		})).isTrue();
		assertThat(seen.get(0)).isSameAs(seen.get(1));
		assertThat(ids()).containsExactly(1, 2);
	}
	
	@Test
	void outsideATransactionEveryConnectionIsItsOwn() throws Exception
	{
		try (Connection first = factory.getConnection(); Connection second = factory.getConnection())
		{
			assertThat(first.unwrap(Connection.class)).isNotSameAs(second.unwrap(Connection.class));
			assertThat(first.getAutoCommit()).isTrue();
		}
	}
	
	/** A step as the game writes them: its own connection, errors logged and swallowed. */
	private static void execute(String sql)
	{
		try (Connection con = factory.getConnection(); Statement statement = con.createStatement())
		{
			statement.execute(sql);
		}
		catch (SQLException e)
		{
			// logged by the game code; the transaction is aborted
		}
	}
	
	private static List<Integer> ids()
	{
		try (Connection con = factory.getConnection(); Statement statement = con.createStatement();
				ResultSet rs = statement.executeQuery("SELECT id FROM public.probe ORDER BY id"))
		{
			List<Integer> ids = new ArrayList<>();
			while (rs.next())
				ids.add(rs.getInt(1));
			return ids;
		}
		catch (SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	private static List<Integer> idsOutside() throws Exception
	{
		Thread[] reader = new Thread[1];
		List<Integer> ids = new ArrayList<>();
		reader[0] = Thread.ofPlatform().start(() -> ids.addAll(ids()));
		reader[0].join();
		return ids;
	}
}
