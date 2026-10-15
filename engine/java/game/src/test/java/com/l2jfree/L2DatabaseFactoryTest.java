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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class L2DatabaseFactoryTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@TempDir
	Path catalog;
	
	@Test
	void connectionsSeeTheWorldAndCatalogSchemasAndTakeUntypedStrings() throws Exception
	{
		L2DatabaseFactory factory = factory();
		try (Connection connection = factory.getConnection(); Statement statement = connection.createStatement())
		{
			try (ResultSet rs = statement.executeQuery("SHOW search_path"))
			{
				rs.next();
				assertThat(rs.getString(1).replace(" ", "")).isEqualTo("world,catalog,public");
			}
			try (PreparedStatement ps = connection.prepareStatement("SELECT 1 + ?"))
			{
				ps.setString(1, "2");
				try (ResultSet rs = ps.executeQuery())
				{
					rs.next();
					assertThat(rs.getInt(1)).isEqualTo(3);
				}
			}
			assertThat(factory.getConnectionPoolStatus()).contains("max=3");
		}
		finally
		{
			factory.shutdown();
		}
	}
	
	@Test
	void preparingTheDatabaseMigratesTheWorldAndLoadsTheCatalog() throws Exception
	{
		Files.writeString(catalog.resolve("teleport.csv"),
				"description,id,x,y,z,price,is_noble_only\n\"Gludio\",1,0,0,0,100,false\n");
		// the schemas exist before the server starts, as the init script of the stack creates them
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		L2DatabaseFactory factory = factory();
		try
		{
			factory.prepareSchemas(catalog);
			try (Connection connection = factory.getConnection(); Statement statement = connection.createStatement();
					ResultSet rs = statement.executeQuery("SELECT (SELECT count(*) FROM teleport), (SELECT count(*) FROM race), "
							+ "to_regclass('report.player_overview') IS NOT NULL"))
			{
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(1);
				assertThat(rs.getInt(2)).isPositive();
				assertThat(rs.getBoolean(3)).as("report views").isTrue();
			}
		}
		finally
		{
			factory.shutdown();
		}
	}
	
	private static L2DatabaseFactory factory()
	{
		return new L2DatabaseFactory(L2DatabaseFactory.poolConfig(POSTGRES.jdbcUrl(), POSTGRES.user(),
				POSTGRES.password(), 3, 1));
	}
}
