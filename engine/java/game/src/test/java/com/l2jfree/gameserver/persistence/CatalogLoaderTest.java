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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class CatalogLoaderTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@TempDir
	Path catalog;
	
	@BeforeAll
	static void migrate()
	{
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
	}
	
	@Test
	void loadsTheCatalogOnceAndAgainWhenItsFilesChange() throws Exception
	{
		writeTeleports("\"Gludio\",1,0,0,0,100,false");
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(CatalogLoader.ensureCurrent(con, catalog)).isTrue();
			assertThat(CatalogLoader.ensureCurrent(con, catalog)).isFalse();
			assertThat(count(con, "SELECT count(*) FROM catalog.teleport")).isEqualTo(1);
			
			writeTeleports("\"Gludio\",1,0,0,0,100,false", "\"Dion\",2,0,0,0,200,true");
			assertThat(CatalogLoader.ensureCurrent(con, catalog)).isTrue();
			assertThat(count(con, "SELECT count(*) FROM catalog.teleport")).isEqualTo(2);
		}
	}
	
	@Test
	void theRevisionIsTheContentOfTheFiles() throws Exception
	{
		writeTeleports("\"Gludio\",1,0,0,0,100,false");
		String first = CatalogLoader.revisionOf(catalog);
		writeTeleports("\"Gludio\",1,0,0,0,100,false");
		assertThat(CatalogLoader.revisionOf(catalog)).isEqualTo(first);
		writeTeleports("\"Gludio\",1,0,0,0,101,false");
		assertThat(CatalogLoader.revisionOf(catalog)).isNotEqualTo(first);
	}
	
	@Test
	void aFileThatFailsToLoadLeavesTheOldCatalogAndItsRevisionInPlace() throws Exception
	{
		writeTeleports("\"Gludio\",1,0,0,0,100,false");
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(CatalogLoader.ensureCurrent(con, catalog)).isTrue();
			String loaded = CatalogLoader.loadedRevision(con);
			
			writeTeleports("\"Gludio\",1,0,0,0,not-a-number,false");
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> CatalogLoader.ensureCurrent(con, catalog))
					.isInstanceOf(java.sql.SQLException.class);
			
			assertThat(CatalogLoader.loadedRevision(con)).isEqualTo(loaded);
			assertThat(count(con, "SELECT count(*) FROM catalog.teleport")).isEqualTo(1);
		}
	}
	
	@Test
	void aFileWithoutATableFailsTheLoadInsteadOfBeingIgnored() throws Exception
	{
		writeTeleports("\"Gludio\",1,0,0,0,100,false");
		Files.writeString(catalog.resolve("no_such_table.csv"), "id\n1\n");
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> CatalogLoader.ensureCurrent(con, catalog))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("no_such_table.csv");
		}
	}
	
	private void writeTeleports(String... rows) throws Exception
	{
		Files.writeString(catalog.resolve("teleport.csv"),
				"description,id,x,y,z,price,is_noble_only\n" + String.join("\n", rows) + "\n");
	}
	
	private static long count(Connection con, String sql) throws Exception
	{
		try (Statement statement = con.createStatement(); ResultSet rs = statement.executeQuery(sql))
		{
			rs.next();
			return rs.getLong(1);
		}
	}
}
