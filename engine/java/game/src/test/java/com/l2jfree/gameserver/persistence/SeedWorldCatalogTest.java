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
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.sql.SchemaMigration;
import com.l2jfree.testing.PostgresWorld;
import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

/** Loads the authored world catalog and checks the row count of each table. */
@Tag("integration")
class SeedWorldCatalogTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	private static final Path CATALOG = Path.of("..", "world", "catalog");
	
	private static final Map<String, Integer> ROWS = Map.ofEntries(Map.entry("player_template", 1),
			Map.entry("level_up_gain", 1), Map.entry("starting_item", 4), Map.entry("weapon_template", 2),
			Map.entry("armor_template", 3), Map.entry("etc_item_template", 2), Map.entry("skill_tree", 5),
			Map.entry("skill_trainer_class", 1), Map.entry("npc_template", 5), Map.entry("drop", 9),
			Map.entry("spawn", 74), Map.entry("merchant_shop", 1), Map.entry("merchant_buylist", 6));
	
	@BeforeAll
	static void migrate()
	{
		PostgresWorld.prepare(POSTGRES, "world", "catalog", "report");
		SchemaMigration.migrate(PostgresWorld.dataSource(POSTGRES), "world", "classpath:db/world");
	}
	
	@Test
	void loadsThirteenTablesAndOneHundredFourteenRows() throws Exception
	{
		try (var files = Files.list(CATALOG))
		{
			assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder(
					"player_template.csv", "level_up_gain.csv", "starting_item.csv", "weapon_template.csv",
					"armor_template.csv", "etc_item_template.csv", "skill_tree.csv", "skill_trainer_class.csv",
					"npc_template.csv", "drop.csv", "spawn.csv", "merchant_shop.csv", "merchant_buylist.csv");
		}
		
		try (Connection con = PostgresWorld.connect(POSTGRES))
		{
			assertThat(CatalogLoader.ensureCurrent(con, CATALOG)).isTrue();
			long total = 0;
			for (Map.Entry<String, Integer> table : ROWS.entrySet())
			{
				long found = count(con, "SELECT count(*) FROM catalog." + table.getKey());
				assertThat(found).as(table.getKey()).isEqualTo(table.getValue().longValue());
				total += found;
			}
			assertThat(total).isEqualTo(114);
		}
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
