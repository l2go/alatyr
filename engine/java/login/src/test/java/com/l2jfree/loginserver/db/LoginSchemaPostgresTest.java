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
package com.l2jfree.loginserver.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginDataAccessException;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;
import com.l2jfree.loginserver.dao.impl.AccountsDAOJdbc;
import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/**
 * The login module against a real PostgreSQL 18: the migration, the pool settings, and the DAOs. The class has a
 * database of its own, prepared as the init script of the stack prepares it: the extension and the empty schema exist
 * before the migration runs.
 */
@Tag("integration")
class LoginSchemaPostgresTest
{
	private static LoginDataSource source;
	private static AccountsDAOJdbc accounts;
	
	@BeforeAll
	static void migrateTheSchema() throws Exception
	{
		TestDatabase database = TestDatabases.postgres();
		PostgresWorld.prepare(database, "login");
		
		source = new LoginDataSource(LoginDataSource.poolConfig("org.postgresql.Driver", database.jdbcUrl(),
				database.user(), database.password(), 4, 1));
		SchemaMigration.migrate(source.getDataSource(), LoginDataSource.SCHEMA, "classpath:db/login");
		
		JdbcTransactions transactions = new JdbcTransactions(source.getDataSource());
		accounts = new AccountsDAOJdbc(transactions);
	}
	
	@AfterAll
	static void closeThePool()
	{
		source.close();
	}
	
	@Test
	@DisplayName("the migration is applied once and a second run applies nothing")
	void migrationIsIdempotent() throws Exception
	{
		assertThat(SchemaMigration.migrate(source.getDataSource(), LoginDataSource.SCHEMA, "classpath:db/login"))
				.isZero();
		
		try (Connection connection = source.getDataSource().getConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'login' "
								+ "AND table_name = 'account'");
				ResultSet result = statement.executeQuery())
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
		}
	}
	
	@Test
	@DisplayName("V2 removed the world server table and left the last world of an account a plain number")
	void gameServerTableIsGone() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement())
		{
			try (ResultSet result = statement.executeQuery("SELECT to_regclass('login.game_server')"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getString(1)).isNull();
			}
			try (ResultSet result = statement.executeQuery("SELECT count(*) FROM pg_constraint "
					+ "WHERE conrelid = 'login.account'::regclass AND contype = 'f'"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isZero();
			}
			try (ResultSet result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history "
					+ "WHERE version = '2' AND success"))
			{
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}
		}
	}
	
	@Test
	@DisplayName("the pool checks out a connection that sees the login schema and sends text untyped")
	void poolUsesTheLoginSchema() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT current_schema()"))
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1)).isEqualTo("login");
		}
		assertThat(source.getPoolStatus()).contains("max=4");
	}
	
	@Test
	@DisplayName("an account keeps its values through the PostgreSQL types and is found without regard to case")
	void accountRoundTrip()
	{
		BigDecimal lastActive = new BigDecimal(1_700_000_123_456L);
		accounts.createAccount(new Accounts("Round_Trip", "hash-1", lastActive, 0, 0, 2000, 2, 29, "192.0.2.7"));
		
		Accounts found = accounts.getAccountById("ROUND_trip");
		
		assertThat(found.getLogin()).isEqualTo("Round_Trip");
		assertThat(found.getPassword()).isEqualTo("hash-1");
		assertThat(found.getLastactive()).isEqualByComparingTo(lastActive);
		assertThat(found.getLastIp()).isEqualTo("192.0.2.7");
		assertThat(found.getLastServerId()).isZero();
		assertThat(found.getBirthYear()).isEqualTo(2000);
		assertThat(found.getBirthMonth()).isEqualTo(2);
		assertThat(found.getBirthDay()).isEqualTo(29);
		assertThat(found.getAccessLevel()).isZero();
	}
	
	@Test
	@DisplayName("an impossible birth date becomes 1900-01-01 and an empty address becomes NULL")
	void impossibleBirthdayAndEmptyAddress()
	{
		accounts.createAccount(new Accounts("Odd_Values", "hash", null, 0, 0, 2001, 2, 30, ""));
		
		Accounts found = accounts.getAccountById("odd_values");
		
		assertThat(found.getBirthYear()).isEqualTo(1900);
		assertThat(found.getBirthMonth()).isEqualTo(1);
		assertThat(found.getBirthDay()).isEqualTo(1);
		assertThat(found.getLastIp()).isNull();
		assertThat(found.getLastactive()).isNull();
	}
	
	@Test
	@DisplayName("createOrUpdate inserts once and then updates only the columns it is given")
	void upsertUpdatesInPlace()
	{
		accounts.createOrUpdate(new Accounts("Upserted", "first", null, 0, 0, 1999, 12, 31, null));
		accounts.createOrUpdate(new Accounts("Upserted", "second", null, 100, null, null, null, null, null));
		
		Accounts found = accounts.getAccountById("upserted");
		
		assertThat(found.getPassword()).isEqualTo("second");
		assertThat(found.getAccessLevel()).isEqualTo(100);
		assertThat(found.getBirthYear()).isEqualTo(1999);
		assertThat(found.getBirthMonth()).isEqualTo(12);
		assertThat(found.getBirthDay()).isEqualTo(31);
	}
	
	@Test
	@DisplayName("update writes every column and reports a missing account")
	void updateWritesAllColumns()
	{
		accounts.createAccount(new Accounts("To_Update", "old", null, 0, 0, 2000, 1, 1, null));
		Accounts account = accounts.getAccountById("to_update");
		account.setPassword("new");
		account.setLastIp("198.51.100.4");
		account.setBirthYear(1990);
		account.setBirthMonth(6);
		account.setBirthDay(15);
		
		accounts.update(account);
		
		Accounts found = accounts.getAccountById("to_update");
		assertThat(found.getPassword()).isEqualTo("new");
		assertThat(found.getLastIp()).isEqualTo("198.51.100.4");
		assertThat(found.getBirthYear()).isEqualTo(1990);
		assertThat(found.getBirthMonth()).isEqualTo(6);
		assertThat(found.getBirthDay()).isEqualTo(15);
		
		assertThatThrownBy(() -> accounts.update(new Accounts("Missing_Account")))
				.isInstanceOf(LoginObjectNotFoundException.class);
	}
	
	@Test
	@DisplayName("updateGiven writes only the columns the bean gives and reports a missing account")
	void updateGivenLeavesTheOtherColumns()
	{
		accounts.createAccount(new Accounts("Partial_User", "hash", null, 50, 3, 2000, 1, 1, "192.0.2.1"));
		
		Accounts login = new Accounts("Partial_User");
		login.setLastactive(BigDecimal.valueOf(1_700_000_000_000L));
		login.setLastIp("198.51.100.9");
		assertThat(accounts.updateGiven(login)).isTrue();
		
		Accounts found = accounts.getAccountById("partial_user");
		assertThat(found.getLastIp()).isEqualTo("198.51.100.9");
		assertThat(found.getLastactive().longValue()).isEqualTo(1_700_000_000_000L);
		assertThat(found.getPassword()).isEqualTo("hash");
		assertThat(found.getAccessLevel()).isEqualTo(50);
		assertThat(found.getLastServerId()).isEqualTo(3);
		
		assertThat(accounts.updateGiven(new Accounts("No_Such_Partial"))).isTrue();
		Accounts missing = new Accounts("No_Such_Partial");
		missing.setLastServerId(1);
		assertThat(accounts.updateGiven(missing)).isFalse();
	}

	@Test
	@DisplayName("a password upgrade replaces the hash only when it is still the hash that was read")
	void passwordUpgradeDoesNotOverwriteANewerHash()
	{
		accounts.createAccount(new Accounts("Rehash_User", "old-hash", null, 0, 0, 2000, 1, 1, null));

		assertThat(accounts.replacePasswordHash("rehash_user", "old-hash", "upgraded")).isTrue();
		assertThat(accounts.getAccountById("rehash_user").getPassword()).isEqualTo("upgraded");

		assertThat(accounts.replacePasswordHash("rehash_user", "old-hash", "from-the-stale-login")).isFalse();
		assertThat(accounts.getAccountById("rehash_user").getPassword()).isEqualTo("upgraded");
		assertThat(accounts.replacePasswordHash("no_such_rehash", "old-hash", "upgraded")).isFalse();
	}
	
	@Test
	@DisplayName("updateAccessLevel is true for an existing account even when the level does not change")
	void updateAccessLevelWithoutChange()
	{
		accounts.createAccount(new Accounts("Level_User", "hash", null, 50, 0, 2000, 1, 1, null));
		
		assertThat(accounts.updateAccessLevel("level_user", 50)).isTrue();
		assertThat(accounts.updateAccessLevel("level_user", 75)).isTrue();
		assertThat(accounts.getAccountById("level_user").getAccessLevel()).isEqualTo(75);
		assertThat(accounts.updateAccessLevel("no_such_user", 1)).isFalse();
	}
	
	@Test
	@DisplayName("the last world of an account is stored without a world server table")
	void lastWorldIsAPlainNumber()
	{
		accounts.createAccount(new Accounts("World_User", "hash", null, 0, 7, 2000, 1, 1, null));
		
		assertThat(accounts.getAccountById("world_user").getLastServerId()).isEqualTo(7);
	}
	
	@Test
	@DisplayName("the table holds the converted values: a moment, a date, an inet address, and a world id")
	void columnsHoldTheConvertedValues() throws Exception
	{
		long lastActive = 1_700_000_000_123L;
		accounts.createAccount(new Accounts("Raw_Columns", "hash", BigDecimal.valueOf(lastActive), 5, 7, 1985, 6, 21,
				"192.168.1.20"));
		
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT last_active_at, birthday_on, host(last_ip), last_world_id "
						+ "FROM account WHERE name = 'raw_columns'"))
		{
			assertThat(rs.next()).isTrue();
			assertThat(rs.getObject(1, java.time.OffsetDateTime.class).toInstant())
					.isEqualTo(java.time.Instant.ofEpochMilli(lastActive));
			assertThat(rs.getObject(2, java.time.LocalDate.class)).isEqualTo(java.time.LocalDate.of(1985, 6, 21));
			assertThat(rs.getString(3)).isEqualTo("192.168.1.20");
			assertThat(rs.getInt(4)).isEqualTo(7);
		}
	}
	
	@Test
	@DisplayName("a last activity of 0 is NULL, and a last world of 0 is NULL")
	void notSetValuesAreStoredAsNull() throws Exception
	{
		accounts.createAccount(new Accounts("Not_Set", "hash", BigDecimal.ZERO, 0, 0, 2000, 1, 1, null));
		
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT last_active_at, last_world_id FROM account "
						+ "WHERE name = 'not_set'"))
		{
			assertThat(rs.next()).isTrue();
			assertThat(rs.getObject(1)).isNull();
			assertThat(rs.getObject(2)).isNull();
		}
	}
	
	@Test
	@DisplayName("an IPv6 address with a zone is stored without the zone")
	void ipv6ZoneIsStripped()
	{
		accounts.createAccount(new Accounts("Ipv6_User", "hash", null, 0, 0, 2000, 1, 1, "fe80::1%eth0"));
		
		assertThat(accounts.getAccountById("ipv6_user").getLastIp()).isEqualTo("fe80::1");
	}
	
	@Test
	@DisplayName("an account is removed one by one and in a batch, and a missing account is reported")
	void accountsAreRemoved()
	{
		for (String name : java.util.List.of("gone_1", "gone_2", "gone_3"))
			accounts.createAccount(new Accounts(name, "hash", null, 0, 0, 2000, 1, 1, null));
		
		accounts.removeAccountById("GONE_1");
		accounts.removeAll(java.util.List.of(new Accounts("gone_2"), new Accounts("gone_3")));
		
		assertThat(accounts.getAllAccounts()).extracting(Accounts::getLogin).doesNotContain("gone_1", "gone_2",
				"gone_3");
		assertThatThrownBy(() -> accounts.removeAccountById("gone_1")).isInstanceOf(LoginObjectNotFoundException.class);
	}
	
	@Test
	@DisplayName("the pool sends strings untyped, so the server types the parameter")
	void poolSendsUntypedStrings() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT 1 + ?"))
		{
			statement.setString(1, "2");
			try (ResultSet rs = statement.executeQuery())
			{
				assertThat(rs.next()).isTrue();
				assertThat(rs.getInt(1)).isEqualTo(3);
			}
		}
	}
	
	@Test
	@DisplayName("a transaction commits its work and rolls back on failure")
	void transactionsCommitAndRollBack() throws Exception
	{
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement())
		{
			statement.execute("CREATE TABLE transaction_probe (value integer NOT NULL)");
		}
		JdbcTransactions transactions = new JdbcTransactions(source.getDataSource());
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO transaction_probe VALUES (?)"))
			{
				statement.setInt(1, 1);
				statement.executeUpdate();
			}
			return null;
		});
		
		assertThatThrownBy(() -> transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO transaction_probe VALUES (?)"))
			{
				statement.setInt(1, 2);
				statement.executeUpdate();
			}
			throw new SQLException("Force rollback");
		})).isInstanceOf(LoginDataAccessException.class);
		
		try (Connection connection = source.getDataSource().getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT value FROM transaction_probe"))
		{
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
			assertThat(result.next()).isFalse();
		}
	}
}
