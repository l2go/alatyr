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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.loginserver.LoginConfig;
import com.zaxxer.hikari.HikariConfig;

class LoginPoolBoundsTest
{
	private String driver;
	private String url;
	private String login;
	private String password;
	private int maximum;
	private int idle;
	
	@BeforeEach
	void rememberPoolSettings()
	{
		driver = LoginConfig.DATABASE_DRIVER;
		url = LoginConfig.DATABASE_URL;
		login = LoginConfig.DATABASE_LOGIN;
		password = LoginConfig.DATABASE_PASSWORD;
		maximum = LoginConfig.DATABASE_MAX_CONNECTIONS;
		idle = LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS;
		LoginConfig.DATABASE_DRIVER = "org.postgresql.Driver";
		LoginConfig.DATABASE_URL = "jdbc:postgresql://127.0.0.1/l2jfree_test";
		LoginConfig.DATABASE_LOGIN = "test";
		LoginConfig.DATABASE_PASSWORD = "test";
	}
	
	@AfterEach
	void restorePoolSettings()
	{
		LoginConfig.DATABASE_DRIVER = driver;
		LoginConfig.DATABASE_URL = url;
		LoginConfig.DATABASE_LOGIN = login;
		LoginConfig.DATABASE_PASSWORD = password;
		LoginConfig.DATABASE_MAX_CONNECTIONS = maximum;
		LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS = idle;
	}
	
	@Test
	@DisplayName("the login pool has a name and registers its state in JMX")
	void theStateOfThePoolCanBeRead()
	{
		HikariConfig pool = LoginDataSource.createPoolConfig();
		
		assertThat(pool.getPoolName()).isEqualTo("l2jfree-loginserver");
		assertThat(pool.isRegisterMbeans()).isTrue();
		assertThat(pool.getConnectionTimeout()).as("the wait for a connection has a ceiling").isEqualTo(30_000);
	}
	
	@Test
	@DisplayName("the login pool keeps at least one connection and caps idle at that maximum")
	void maximumHasAFloorOfOne()
	{
		LoginConfig.DATABASE_MAX_CONNECTIONS = 0;
		LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS = 5;
		
		HikariConfig pool = LoginDataSource.createPoolConfig();
		
		assertThat(pool.getMaximumPoolSize()).isEqualTo(1);
		assertThat(pool.getMinimumIdle()).isEqualTo(1);
	}
	
	@Test
	@DisplayName("a negative login idle floor becomes zero")
	void negativeIdleBecomesZero()
	{
		LoginConfig.DATABASE_MAX_CONNECTIONS = 8;
		LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS = -1;
		
		HikariConfig pool = LoginDataSource.createPoolConfig();
		
		assertThat(pool.getMaximumPoolSize()).isEqualTo(8);
		assertThat(pool.getMinimumIdle()).isZero();
	}
}
