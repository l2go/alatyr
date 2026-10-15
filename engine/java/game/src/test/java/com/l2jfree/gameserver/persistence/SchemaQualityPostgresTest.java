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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Checks the database conventions that a machine can check, on a freshly migrated database. A table
 * that breaks a rule fails the build and the failure names the table and the rule.
 */
@Tag("integration")
@Testcontainers
class SchemaQualityPostgresTest
{
	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
	
	private static final Path CATALOG = WorldDatabase.CATALOG;
	
	/** The schemas whose tables the rules cover. */
	private static final String SCHEMAS = "('world', 'catalog')";
	
	private static HikariDataSource source;
	
	@BeforeAll
	static void migrate() throws Exception
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
		config.setMaximumPoolSize(2);
		config.addDataSourceProperty("stringtype", "unspecified");
		config.addDataSourceProperty("currentSchema", "world,catalog,public");
		source = new HikariDataSource(config);
		
		WorldSchemas.prepare(source, CATALOG);
	}
	
	@AfterAll
	static void close()
	{
		source.close();
	}
	
	@Test
	@DisplayName("every schema has a comment and nothing but extension objects lives in public")
	void schemasAreDocumentedAndPublicIsEmpty() throws Exception
	{
		assertThat(strings("SELECT nspname FROM pg_namespace WHERE nspname IN ('world', 'catalog', 'report') "
				+ "AND obj_description(oid, 'pg_namespace') IS NULL")).as("schemas without a comment").isEmpty();
		assertThat(strings("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
				+ "WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'S')")).as("relations in public").isEmpty();
	}
	
	@Test
	@DisplayName("every table has a primary key")
	void everyTableHasAPrimaryKey() throws Exception
	{
		assertThat(strings(tables() + " AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = t.oid "
				+ "AND k.contype = 'p')")).as("tables without a primary key").isEmpty();
	}
	
	@Test
	@DisplayName("every table and every column except id has a comment")
	void everythingIsDocumented() throws Exception
	{
		assertThat(strings(tables() + " AND obj_description(t.oid, 'pg_class') IS NULL")).as("tables without a comment")
				.isEmpty();
		
		assertThat(strings("SELECT n.nspname || '.' || t.relname || '.' || a.attname FROM pg_class t "
				+ "JOIN pg_namespace n ON n.oid = t.relnamespace JOIN pg_attribute a ON a.attrelid = t.oid "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') AND t.relname <> 'flyway_schema_history' "
				+ "AND a.attnum > 0 AND NOT a.attisdropped AND a.attname <> 'id' "
				+ "AND col_description(t.oid, a.attnum) IS NULL")).as("columns without a comment").isEmpty();
	}
	
	@Test
	@DisplayName("names are lower snake case ASCII and no name is a reserved word")
	void namesFollowTheConvention() throws Exception
	{
		assertThat(strings("SELECT n.nspname || '.' || t.relname FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') AND t.relname !~ '^[a-z][a-z0-9_]*$'"))
				.as("table names that are not snake case").isEmpty();
		assertThat(strings("SELECT n.nspname || '.' || t.relname || '.' || a.attname FROM pg_class t "
				+ "JOIN pg_namespace n ON n.oid = t.relnamespace JOIN pg_attribute a ON a.attrelid = t.oid "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') AND a.attnum > 0 AND NOT a.attisdropped "
				+ "AND a.attname !~ '^[a-z][a-z0-9_]*$'")).as("column names that are not snake case").isEmpty();
		
		assertThat(strings("SELECT n.nspname || '.' || t.relname FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') "
				+ "AND t.relname IN (SELECT word FROM pg_get_keywords() WHERE catcode = 'R')"))
				.as("tables named like a reserved word").isEmpty();
		assertThat(strings("SELECT n.nspname || '.' || t.relname || '.' || a.attname FROM pg_class t "
				+ "JOIN pg_namespace n ON n.oid = t.relnamespace JOIN pg_attribute a ON a.attrelid = t.oid "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') AND a.attnum > 0 AND NOT a.attisdropped "
				+ "AND a.attname IN (SELECT word FROM pg_get_keywords() WHERE catcode = 'R')"))
				.as("columns named like a reserved word").isEmpty();
	}
	
	@Test
	@DisplayName("a moment is a timestamptz whose name ends in _at, and no timestamp lacks a time zone")
	void momentsAreTimestamptz() throws Exception
	{
		assertThat(strings("SELECT table_schema || '.' || table_name || '.' || column_name FROM information_schema.columns "
				+ "WHERE table_schema IN " + SCHEMAS + " AND table_name <> 'flyway_schema_history' "
				+ "AND data_type = 'timestamp without time zone'")).as("timestamps without a time zone").isEmpty();
		assertThat(strings("SELECT table_schema || '.' || table_name || '.' || column_name FROM information_schema.columns "
				+ "WHERE table_schema IN " + SCHEMAS + " AND table_name <> 'flyway_schema_history' "
				+ "AND data_type = 'timestamp with time zone' AND column_name !~ '_at$'"))
				.as("moments whose name does not end in _at").isEmpty();
	}
	
	@Test
	@DisplayName("every foreign key has an index that starts with its columns")
	void foreignKeysAreIndexed() throws Exception
	{
		List<String> unindexed = new ArrayList<String>();
		try (Connection connection = source.getConnection(); Statement statement = connection.createStatement())
		{
			Set<String> prefixes = new HashSet<String>();
			try (ResultSet result = statement.executeQuery(
					"SELECT indrelid::oid::text, indkey::text FROM pg_index"))
			{
				while (result.next())
				{
					String[] columns = result.getString(2).split(" ");
					for (int length = 1; length <= columns.length; length++)
					{
						prefixes.add(result.getString(1) + ":" + String.join(",", Arrays.copyOf(columns, length)));
					}
				}
			}
			
			try (ResultSet result = statement.executeQuery(
					"SELECT k.conrelid::oid::text, array_to_string(k.conkey, ','), n.nspname || '.' || t.relname || '.' || k.conname "
							+ "FROM pg_constraint k JOIN pg_class t ON t.oid = k.conrelid "
							+ "JOIN pg_namespace n ON n.oid = t.relnamespace "
							+ "WHERE k.contype = 'f' AND n.nspname IN " + SCHEMAS))
			{
				while (result.next())
				{
					if (!prefixes.contains(result.getString(1) + ":" + result.getString(2)))
					{
						unindexed.add(result.getString(3));
					}
				}
			}
		}
		
		assertThat(unindexed).as("foreign keys without an index").isEmpty();
	}
	
	private static String tables()
	{
		return "SELECT n.nspname || '.' || t.relname FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace "
				+ "WHERE n.nspname IN " + SCHEMAS + " AND t.relkind IN ('r', 'p') "
				+ "AND t.relname NOT IN ('flyway_schema_history')";
	}
	
	private static List<String> strings(String query) throws Exception
	{
		List<String> values = new ArrayList<String>();
		try (Connection connection = source.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(query))
		{
			while (result.next())
			{
				values.add(result.getString(1));
			}
		}
		return values;
	}
}
