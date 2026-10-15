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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariConfig;

class GamePoolBoundsTest
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
		driver = Config.DATABASE_DRIVER;
		url = Config.DATABASE_URL;
		login = Config.DATABASE_LOGIN;
		password = Config.DATABASE_PASSWORD;
		maximum = Config.DATABASE_MAX_CONNECTIONS;
		idle = Config.DATABASE_MIN_IDLE_CONNECTIONS;
		Config.DATABASE_DRIVER = "org.postgresql.Driver";
		Config.DATABASE_URL = "jdbc:postgresql://127.0.0.1/l2jfree_test";
		Config.DATABASE_LOGIN = "test";
		Config.DATABASE_PASSWORD = "test";
	}
	
	@AfterEach
	void restorePoolSettings()
	{
		Config.DATABASE_DRIVER = driver;
		Config.DATABASE_URL = url;
		Config.DATABASE_LOGIN = login;
		Config.DATABASE_PASSWORD = password;
		Config.DATABASE_MAX_CONNECTIONS = maximum;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = idle;
	}
	
	@Test
	@DisplayName("the pool has a name and registers its state in JMX")
	void theStateOfThePoolCanBeRead()
	{
		HikariConfig pool = L2DatabaseFactory.createPoolConfig();
		
		assertThat(pool.getPoolName()).isEqualTo("l2jfree-gameserver");
		assertThat(pool.isRegisterMbeans()).isTrue();
		assertThat(pool.getConnectionTimeout()).as("the wait for a connection has a ceiling").isEqualTo(30_000);
	}
	
	@Test
	@DisplayName("idle connections cannot exceed the configured maximum")
	void idleIsCappedAtTheMaximum()
	{
		Config.DATABASE_MAX_CONNECTIONS = 50;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = 80;
		
		HikariConfig pool = L2DatabaseFactory.createPoolConfig();
		
		assertThat(pool.getMaximumPoolSize()).isEqualTo(50);
		assertThat(pool.getMinimumIdle()).isEqualTo(50);
	}
	
	@Test
	@DisplayName("a maximum below ten is raised, and a smaller idle floor stays")
	void maximumHasAFloorOfTen()
	{
		Config.DATABASE_MAX_CONNECTIONS = 4;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = 2;
		
		HikariConfig pool = L2DatabaseFactory.createPoolConfig();
		
		assertThat(Config.DATABASE_MAX_CONNECTIONS).isEqualTo(10);
		assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
		assertThat(pool.getMinimumIdle()).isEqualTo(2);
	}
	
	@Test
	@DisplayName("a negative idle floor becomes zero")
	void negativeIdleBecomesZero()
	{
		Config.DATABASE_MAX_CONNECTIONS = 20;
		Config.DATABASE_MIN_IDLE_CONNECTIONS = -3;
		
		HikariConfig pool = L2DatabaseFactory.createPoolConfig();
		
		assertThat(pool.getMinimumIdle()).isZero();
		assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
	}
}
