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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The world schemas on a real PostgreSQL 18: the migrations, the catalog load with its real files, the report views,
 * and the lock that keeps a second server away.
 */
@Tag("integration")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorldSchemasPostgresTest
{
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
	
	/** The catalog of the authored world. Tests run with the module directory as the working directory. */
	private static final Path CATALOG = WorldDatabase.CATALOG;
	
	private static HikariDataSource source;
	
	@BeforeAll
	static void createTheSchemasAndPrepareThem() throws Exception
	{
		try (Connection connection = POSTGRES.createConnection(""); Statement statement = connection.createStatement())
		{
			statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA public");
			statement.execute("CREATE SCHEMA world");
			statement.execute("CREATE SCHEMA catalog");
			statement.execute("CREATE SCHEMA report");
		}
		
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(POSTGRES.getJdbcUrl());
		config.setUsername(POSTGRES.getUsername());
		config.setPassword(POSTGRES.getPassword());
		config.setMaximumPoolSize(4);
		config.addDataSourceProperty("stringtype", "unspecified");
		config.addDataSourceProperty("currentSchema", "world,catalog,public");
		source = new HikariDataSource(config);
		
		WorldSchemas.prepare(source, CATALOG);
	}
	
	@AfterAll
	static void closeThePool()
	{
		source.close();
	}
	
	@Test
	@Order(1)
	@DisplayName("the world migrations are applied and the world tables exist")
	void worldTablesExist() throws Exception
	{
		assertThat(count("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'world' "
				+ "AND table_name IN ('player', 'item', 'clan', 'castle')")).isEqualTo(4);
		assertThat(count("SELECT count(*) FROM player")).isZero();
	}
	
	@Test
	@Order(2)
	@DisplayName("the catalog holds the revision of the files and their rows")
	void catalogIsLoaded() throws Exception
	{
		assertThat(string("SELECT revision FROM catalog.catalog_revision")).isEqualTo(CatalogLoader.revisionOf(CATALOG));
		// Authored world: 5 npcs, 2 weapons, 5 skill-tree rows. Item names are 2 weapons, 3 armor, and 2 etc items.
		assertThat(count("SELECT count(*) FROM catalog.npc_template")).isEqualTo(5);
		assertThat(count("SELECT count(*) FROM catalog.weapon_template")).isEqualTo(2);
		assertThat(count("SELECT count(*) FROM catalog.skill_tree")).isEqualTo(5);
	}
	
	@Test
	@Order(3)
	@DisplayName("the report views read the world and the catalog")
	void reportViewsWork() throws Exception
	{
		assertThat(count("SELECT count(*) FROM report.player_overview")).isZero();
		assertThat(count("SELECT count(*) FROM report.item_name")).isEqualTo(7);
	}
	
	@Test
	@Order(4)
	@DisplayName("a second start with the same files does not load the catalog again")
	void currentCatalogIsNotReloaded() throws Exception
	{
		Timestamp before = timestamp("SELECT loaded_at FROM catalog.catalog_revision");
		
		try (Connection connection = source.getConnection())
		{
			assertThat(CatalogLoader.ensureCurrent(connection, CATALOG)).isFalse();
		}
		WorldSchemas.prepare(source, CATALOG);
		
		assertThat(timestamp("SELECT loaded_at FROM catalog.catalog_revision")).isEqualTo(before);
		assertThat(count("SELECT count(*) FROM report.item_name")).isEqualTo(7);
	}
	
	@Test
	@Order(5)
	@DisplayName("the load order puts every referenced table before the table that references it")
	void loadOrderFollowsForeignKeys() throws Exception
	{
		try (Connection connection = source.getConnection())
		{
			var order = CatalogLoader.loadOrder(connection);
			
			assertThat(order).contains("npc_template", "spawn", "weapon_template");
			assertThat(order).doesNotContain("catalog_revision");
			assertThat(order.indexOf("npc_template")).isLessThan(order.indexOf("spawn"));
		}
	}
	
	@Test
	@Order(6)
	@DisplayName("a second server on the same database is refused until the first one lets go")
	void worldLockIsExclusive()
	{
		try (WorldLock first = WorldLock.acquire(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
		{
			assertThat(first).isNotNull();
			assertThatThrownBy(
					() -> WorldLock.acquire(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("Another server");
		}
		
		try (WorldLock again = WorldLock.acquire(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()))
		{
			assertThat(again).isNotNull();
		}
	}
	
	private static int count(String sql) throws Exception
	{
		try (Connection connection = source.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql))
		{
			result.next();
			return result.getInt(1);
		}
	}
	
	private static String string(String sql) throws Exception
	{
		try (Connection connection = source.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql))
		{
			result.next();
			return result.getString(1);
		}
	}
	
	private static Timestamp timestamp(String sql) throws Exception
	{
		try (Connection connection = source.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql))
		{
			result.next();
			return result.getTimestamp(1);
		}
	}
}
