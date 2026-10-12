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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/** The {@code report} schema: read-only views for operators. */
@Tag("integration")
class ReportViewsTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	private static final String PASSWORD = "report-test";
	
	@TempDir
	static Path catalog;
	
	@BeforeAll
	static void prepare() throws Exception
	{
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
		Files.writeString(catalog.resolve("etc_item_template.csv"), "id,name\n57,Adena\n");
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			statement.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '"
					+ ReportViews.READER_ROLE + "') THEN CREATE ROLE " + ReportViews.READER_ROLE
					+ " LOGIN; END IF; END $$");
			statement.execute("ALTER ROLE " + ReportViews.READER_ROLE + " PASSWORD '" + PASSWORD + "'");
			statement.execute("DELETE FROM world.item WHERE id = 900001");
			statement.execute("UPDATE world.player SET clan_id = NULL WHERE id IN (900002, 900003)");
			statement.execute("DELETE FROM world.clan WHERE id = 900010");
			statement.execute("DELETE FROM world.player WHERE id IN (900002, 900003)");
			statement.execute("INSERT INTO world.player (id, account_name, name, race_id, active_class_id, base_class_id, "
					+ "level, is_online, last_access_at) VALUES (900002, 'report_acc', 'ReportLeader', 0, 1, 0, 40, true, "
					+ "'2026-10-01 12:00:00+00'), (900003, 'report_acc', 'ReportMember', 3, 44, 44, 20, false, NULL)");
			statement.execute("INSERT INTO world.clan (id, name, level, leader_player_id) VALUES (900010, 'ReportClan', 3, 900002)");
			statement.execute("UPDATE world.player SET clan_id = 900010 WHERE id IN (900002, 900003)");
			statement.execute("INSERT INTO world.item (id, item_template_id, owner_player_id, location, count) "
					+ "VALUES (900001, 57, 900002, 'INVENTORY', 1500)");
		}
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			CatalogLoader.load(con, catalog, "report-test");
			ReportViews.recreate(con);
		}
	}
	
	@AfterAll
	static void dropReader() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			statement.execute("DROP OWNED BY " + ReportViews.READER_ROLE);
			statement.execute("DROP ROLE " + ReportViews.READER_ROLE);
		}
	}
	
	@Test
	void playerOverviewNamesRaceClassAndClan() throws Exception
	{
		assertThat(rows("SELECT player_name, account_name, race, class, level, clan_name, is_online, "
				+ "last_login_at IS NOT NULL FROM report.player_overview WHERE account_name = 'report_acc' ORDER BY 1"))
				.containsExactly("ReportLeader|report_acc|Human|Warrior|40|ReportClan|t|t",
						"ReportMember|report_acc|Orc|Fighter|20|ReportClan|f|f");
	}
	
	@Test
	void playerInventoryNamesTheItems() throws Exception
	{
		assertThat(rows("SELECT player_name, item_name, location, count FROM report.player_inventory "
				+ "WHERE player_name = 'ReportLeader'")).containsExactly("ReportLeader|Adena|INVENTORY|1500");
	}
	
	@Test
	void clanOverviewCountsTheMembers() throws Exception
	{
		assertThat(rows("SELECT clan_name, level, leader_name, member_count FROM report.clan_overview "
				+ "WHERE clan_name = 'ReportClan'")).containsExactly("ReportClan|3|ReportLeader|2");
	}
	
	@Test
	void theViewsSurviveACatalogReload() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			CatalogLoader.load(con, catalog, "report-test-2");
		}
		assertThat(rows("SELECT item_name FROM report.player_inventory WHERE player_name = 'ReportLeader'"))
				.containsExactly("Adena");
		assertThat(readerRows("SELECT count(*) FROM catalog.etc_item_template")).containsExactly("1");
	}
	
	@Test
	void afterDropMigrationsMayChangeTheColumnsTheViewsRead() throws Exception
	{
		try (Connection con = PostgresWorld.connect(POSTGRES); Statement statement = con.createStatement())
		{
			con.setAutoCommit(false);
			try
			{
				ReportViews.drop(con);
				statement.execute("ALTER TABLE world.player ALTER COLUMN karma TYPE bigint");
				assertThat(rows(con, "SELECT count(*) FROM pg_views WHERE schemaname = 'report'")).containsExactly("0");
			}
			finally
			{
				con.rollback();
			}
		}
	}
	
	@Test
	void everyViewAndColumnIsDescribed() throws Exception
	{
		assertThat(rows("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
				+ "WHERE n.nspname = 'report' AND c.relkind = 'v' AND obj_description(c.oid, 'pg_class') IS NULL"))
				.isEmpty();
		assertThat(rows("SELECT c.relname || '.' || a.attname FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid "
				+ "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'report' AND a.attnum > 0 "
				+ "AND col_description(c.oid, a.attnum) IS NULL")).isEmpty();
		assertThat(rows("SELECT obj_description(oid, 'pg_namespace') IS NOT NULL FROM pg_namespace "
				+ "WHERE nspname = 'report'")).containsExactly("t");
	}
	
	@Test
	void theReaderRoleReadsTheReportsAndTheCatalog() throws Exception
	{
		assertThat(readerRows("SELECT player_name FROM report.player_overview WHERE account_name = 'report_acc' "
				+ "ORDER BY 1")).containsExactly("ReportLeader", "ReportMember");
		assertThat(readerRows("SELECT name FROM catalog.etc_item_template")).containsExactly("Adena");
	}
	
	@Test
	void theReaderRoleCannotReadOrChangeTheWorld() throws Exception
	{
		for (String sql : List.of("SELECT count(*) FROM world.player", "DELETE FROM catalog.etc_item_template",
				"CREATE VIEW report.probe AS SELECT 1"))
		{
			try (Connection con = connectAsReader(); Statement statement = con.createStatement())
			{
				assertThatThrownBy(() -> statement.execute(sql)).as(sql).isInstanceOf(SQLException.class)
						.extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("42501");
			}
		}
	}
	
	private static List<String> rows(String sql) throws SQLException
	{
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			return rows(con, sql);
		}
	}
	
	private static List<String> readerRows(String sql) throws SQLException
	{
		try (Connection con = connectAsReader())
		{
			return rows(con, sql);
		}
	}
	
	private static List<String> rows(Connection con, String sql) throws SQLException
	{
		List<String> rows = new ArrayList<>();
		try (Statement statement = con.createStatement(); ResultSet rs = statement.executeQuery(sql))
		{
			int columns = rs.getMetaData().getColumnCount();
			while (rs.next())
			{
				List<String> values = new ArrayList<>();
				for (int i = 1; i <= columns; i++)
				{
					values.add(rs.getString(i));
				}
				rows.add(String.join("|", values));
			}
		}
		return rows;
	}
	
	private static Connection connectAsReader() throws SQLException
	{
		return DriverManager.getConnection(POSTGRES.jdbcUrl(), ReportViews.READER_ROLE, PASSWORD);
	}
}
