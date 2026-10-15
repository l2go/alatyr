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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;

/**
 * Writes the data dictionary and the entity relationship diagram of each schema from the migrated database and checks
 * that the committed files in docs/database are the same.
 * <p>
 * The dictionary is generated, never edited by hand. When a migration changes a table, this test fails and writes the new
 * files to <code>target/database-docs</code>; copying them to <code>docs/database</code> and committing them is the
 * documentation change that belongs to the migration.
 */
@Tag("integration")
class DataDictionaryPostgresTest
{
	private static final Path COMMITTED = Path.of("..", "docs", "database");
	
	private static final Path GENERATED = Path.of("target", "database-docs");
	
	@Test
	@DisplayName("the committed data dictionary equals the one generated from the migrated database")
	void committedDictionaryIsCurrent() throws Exception
	{
		L2DatabaseFactory factory = WorldDatabase.start();
		
		List<String> stale = new ArrayList<String>();
		Files.createDirectories(GENERATED);
		try (Connection connection = factory.getPoolConnection())
		{
			for (String schema : List.of("world", "catalog"))
			{
				String generated = describe(connection, schema);
				Files.writeString(GENERATED.resolve(schema + ".md"), generated, StandardCharsets.UTF_8);
				
				Path committed = COMMITTED.resolve(schema + ".md");
				String current = Files.exists(committed) ? Files.readString(committed, StandardCharsets.UTF_8) : null;
				if (!generated.equals(current))
				{
					stale.add(schema + ".md");
				}
			}
		}
		
		assertThat(stale).as("files of docs/database that differ from the generated ones; copy them from "
				+ "l2jfree-core/target/database-docs").isEmpty();
	}
	
	private static String describe(Connection connection, String schema) throws SQLException, IOException
	{
		StringBuilder text = new StringBuilder();
		text.append("# Data dictionary: schema `").append(schema).append("`\n\n");
		text.append("<!-- Generated from the migrated database by DataDictionaryPostgresTest. Do not edit by hand. -->\n\n");
		text.append(schemaComment(connection, schema)).append("\n\n");
		
		Map<String, String> tables = tables(connection, schema);
		
		text.append("## Entity relationships\n\n```mermaid\nerDiagram\n");
		for (String line : relationships(connection, schema))
		{
			text.append("    ").append(line).append('\n');
		}
		for (String table : tables.keySet())
		{
			text.append("    ").append(table).append('\n');
		}
		text.append("```\n\n");
		
		text.append("## Tables\n\n| Table | Description |\n|---|---|\n");
		for (Map.Entry<String, String> table : tables.entrySet())
		{
			text.append("| [`").append(table.getKey()).append("`](#").append(table.getKey()).append(") | ")
					.append(cell(table.getValue())).append(" |\n");
		}
		text.append('\n');
		
		for (String table : tables.keySet())
		{
			describeTable(connection, schema, table, tables.get(table), text);
		}
		return text.toString();
	}
	
	private static void describeTable(Connection connection, String schema, String table, String comment, StringBuilder text)
			throws SQLException
	{
		text.append("## ").append(table).append("\n\n").append(comment).append("\n\n");
		text.append("| Column | Type | Null | Default | Description |\n|---|---|---|---|---|\n");
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT a.attname, format_type(a.atttypid, a.atttypmod), NOT a.attnotnull, "
						+ "pg_get_expr(d.adbin, d.adrelid), col_description(c.oid, a.attnum) "
						+ "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
						+ "JOIN pg_attribute a ON a.attrelid = c.oid "
						+ "LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum "
						+ "WHERE n.nspname = ? AND c.relname = ? AND a.attnum > 0 AND NOT a.attisdropped ORDER BY a.attnum"))
		{
			statement.setString(1, schema);
			statement.setString(2, table);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					text.append("| `").append(result.getString(1)).append("` | ").append(cell(result.getString(2)))
							.append(" | ").append(result.getBoolean(3) ? "yes" : "no").append(" | ")
							.append(result.getString(4) == null ? "" : "`" + cell(result.getString(4)) + "`").append(" | ")
							.append(cell(result.getString(5))).append(" |\n");
				}
			}
		}
		
		text.append('\n');
		constraints(connection, schema, table, text);
		indexes(connection, schema, table, text);
	}
	
	private static void constraints(Connection connection, String schema, String table, StringBuilder text)
			throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT k.contype, pg_get_constraintdef(k.oid) FROM pg_constraint k "
						+ "JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
						+ "WHERE n.nspname = ? AND c.relname = ? AND k.contype IN ('p', 'u', 'f', 'c') "
						+ "ORDER BY k.contype, k.conname"))
		{
			statement.setString(1, schema);
			statement.setString(2, table);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					String kind = switch (result.getString(1))
					{
						case "p" -> "Primary key";
						case "u" -> "Unique";
						case "f" -> "Foreign key";
						default -> "Check";
					};
					text.append("- ").append(kind).append(": `").append(cell(result.getString(2))).append("`\n");
				}
			}
		}
	}
	
	private static void indexes(Connection connection, String schema, String table, StringBuilder text) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND tablename = ? ORDER BY indexname"))
		{
			statement.setString(1, schema);
			statement.setString(2, table);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					text.append("- Index: `").append(cell(result.getString(1).replace(schema + ".", ""))).append("`\n");
				}
			}
		}
		text.append('\n');
	}
	
	private static String schemaComment(Connection connection, String schema) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT obj_description(oid, 'pg_namespace') FROM pg_namespace WHERE nspname = ?"))
		{
			statement.setString(1, schema);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() && result.getString(1) != null ? result.getString(1) : "";
			}
		}
	}
	
	/** The tables of the schema with their comments, in name order. */
	private static Map<String, String> tables(Connection connection, String schema) throws SQLException
	{
		Map<String, String> tables = new LinkedHashMap<String, String>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT c.relname, obj_description(c.oid, 'pg_class') FROM pg_class c "
						+ "JOIN pg_namespace n ON n.oid = c.relnamespace "
						+ "WHERE n.nspname = ? AND c.relkind IN ('r', 'p') AND c.relname <> 'flyway_schema_history' "
						+ "ORDER BY c.relname"))
		{
			statement.setString(1, schema);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					tables.put(result.getString(1), result.getString(2) == null ? "" : result.getString(2));
				}
			}
		}
		return tables;
	}
	
	/** One line of the diagram for every foreign key inside the schema: the referenced table owns the rows of the other. */
	private static List<String> relationships(Connection connection, String schema) throws SQLException
	{
		List<String> lines = new ArrayList<String>();
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT DISTINCT parent.relname, child.relname FROM pg_constraint k "
						+ "JOIN pg_class child ON child.oid = k.conrelid JOIN pg_class parent ON parent.oid = k.confrelid "
						+ "JOIN pg_namespace cn ON cn.oid = child.relnamespace JOIN pg_namespace pn ON pn.oid = parent.relnamespace "
						+ "WHERE k.contype = 'f' AND cn.nspname = ? AND pn.nspname = ? ORDER BY 1, 2"))
		{
			statement.setString(1, schema);
			statement.setString(2, schema);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					lines.add(result.getString(1) + " ||--o{ " + result.getString(2) + " : references");
				}
			}
		}
		return lines;
	}
	
	/** Makes a value safe inside a Markdown table cell. */
	private static String cell(String value)
	{
		return value == null ? "" : value.replace("|", "\\|").replace("\n", " ").strip();
	}
}
