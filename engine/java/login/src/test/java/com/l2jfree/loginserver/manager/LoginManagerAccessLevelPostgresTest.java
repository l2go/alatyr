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
package com.l2jfree.loginserver.manager;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.impl.AccountsDAOJdbc;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/**
 * A ban issued in the world reaches the login as a change of the access level and is stored in {@code login.account}
 * on PostgreSQL 18. In the process of the 2.x line this was the packet ChangeAccessLevel.
 */
@Tag("integration")
class LoginManagerAccessLevelPostgresTest
{
	private static AccountsServices accounts;
	
	@BeforeAll
	static void migrateTheSchema() throws Exception
	{
		TestDatabase database = TestDatabases.postgres();
		PostgresWorld.prepare(database, "login");
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(database.jdbcUrl());
		dataSource.setUser(database.user());
		dataSource.setPassword(database.password());
		dataSource.setCurrentSchema("login,public");
		SchemaMigration.migrate(dataSource, "login", "classpath:db/login");
		
		accounts = new AccountsServices();
		accounts.setAccountsDAO(new AccountsDAOJdbc(new JdbcTransactions(dataSource)));
	}
	
	@Test
	void theNewLevelIsStoredAndAMissingAccountIsLeftAlone() throws Exception
	{
		accounts.addOrUpdateAccount(new Accounts("banned", "hash", BigDecimal.ZERO, 0, 0, 1900, 1, 1, "192.0.2.1"));
		LoginManager manager = new LoginManager(accounts);
		
		manager.setAccountAccessLevel("banned", -1);
		manager.setAccountAccessLevel("missing", -1);
		
		assertThat(accounts.getAccountById("banned").getAccessLevel()).isEqualTo(-1);
		assertThat(accounts.exists("missing")).isFalse();
	}
}
