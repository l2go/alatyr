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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.l2jfree.gameserver.persistence.WorldTransaction;

/** The connection pool of the world module and the transaction that joins everything on its thread. */
@Tag("integration")
@Testcontainers
class L2DatabaseFactoryPostgresTest
{
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
	
	private static L2DatabaseFactory factory;
	
	@BeforeAll
	static void startThePool() throws Exception
	{
		try (Connection connection = POSTGRES.createConnection(""); Statement statement = connection.createStatement())
		{
			statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA public");
			statement.execute("CREATE SCHEMA world");
			statement.execute("CREATE SCHEMA catalog");
			statement.execute("CREATE TABLE world.transaction_probe (value integer NOT NULL)");
			statement.execute("CREATE TABLE world.name_probe (name citext PRIMARY KEY)");
		}
		
		Config.DATABASE_DRIVER = "org.postgresql.Driver";
		Config.DATABASE_URL = POSTGRES.getJdbcUrl();
		Config.DATABASE_LOGIN = POSTGRES.getUsername();
		Config.DATABASE_PASSWORD = POSTGRES.getPassword();
		Config.DATABASE_MAX_CONNECTIONS = 10;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = 1;
		factory = new L2DatabaseFactory(L2DatabaseFactory.createPoolConfig());
	}
	
	@AfterAll
	static void stopThePool() throws Exception
	{
		factory.shutdown();
	}
	
	@Test
	@DisplayName("a connection sees the world, catalog, and public schemas in this order")
	void searchPath() throws Exception
	{
		try (Connection connection = factory.getConnection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SHOW search_path"))
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1).replace(" ", "")).isEqualTo("world,catalog,public");
		}
	}
	
	@Test
	@DisplayName("a name is compared without regard to case because text is sent untyped")
	void namesIgnoreCase() throws Exception
	{
		try (Connection connection = factory.getConnection())
		{
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO name_probe (name) VALUES (?)"))
			{
				statement.setString(1, "Aragorn");
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM name_probe WHERE name = ?"))
			{
				statement.setString(1, "ARAGORN");
				try (ResultSet result = statement.executeQuery())
				{
					result.next();
					assertThat(result.getInt(1)).isEqualTo(1);
				}
			}
		}
	}
	
	@Test
	@DisplayName("work that returns is committed and work that throws is rolled back")
	void transactionCommitsAndRollsBack() throws Exception
	{
		boolean committed = WorldTransaction.run("commit", factory::getPoolConnection, () -> insert(1));
		boolean rolledBack = WorldTransaction.run("rollback", factory::getPoolConnection, () -> {
			insert(2);
			throw new IllegalStateException("force a rollback");
		});
		
		assertThat(committed).isTrue();
		assertThat(rolledBack).isFalse();
		assertThat(values()).contains(1).doesNotContain(2);
	}
	
	@Test
	@DisplayName("a call made inside a transaction joins it, so a failure later in the work undoes the earlier writes")
	void everythingOnTheThreadJoinsTheTransaction() throws Exception
	{
		boolean result = WorldTransaction.run("join", factory::getPoolConnection, () -> {
			insert(10);
			WorldTransaction.run("inner", factory::getPoolConnection, () -> insert(11));
			throw new IllegalStateException("fail after both writes");
		});
		
		assertThat(result).isFalse();
		assertThat(values()).doesNotContain(10, 11);
	}
	
	private static void insert(int value)
	{
		try (Connection connection = factory.getConnection();
				PreparedStatement statement = connection.prepareStatement("INSERT INTO transaction_probe (value) VALUES (?)"))
		{
			statement.setInt(1, value);
			statement.executeUpdate();
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	private static java.util.List<Integer> values() throws Exception
	{
		java.util.List<Integer> values = new java.util.ArrayList<Integer>();
		try (Connection connection = factory.getPoolConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT value FROM transaction_probe"))
		{
			while (result.next())
			{
				values.add(result.getInt(1));
			}
		}
		return values;
	}
}
