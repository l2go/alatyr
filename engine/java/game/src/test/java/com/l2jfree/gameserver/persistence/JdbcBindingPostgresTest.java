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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;

/**
 * Every JDBC accessor of the game server against the type PostgreSQL infers.
 * <p>
 * {@link GameServerStatementsPostgresTest} proves that the names and the syntax of the statements are right. This test
 * proves that the code reads and writes them with the right types: it finds each {@code prepareStatement} with SQL
 * known at compile time in the sources, and checks every {@code setXxx(index, ...)} against the type of the parameter
 * and every {@code getXxx("column")} against the type of the result column, as PostgreSQL describes them. A mismatch
 * that pgjdbc accepts silently, such as {@code getString} on a {@code boolean}, is an error here, and so is one that
 * fails only when the statement runs.
 * <p>
 * Known exceptions are listed with their reasons in {@code db/jdbc-binding-exceptions.txt}; the list must be empty or
 * every entry must name a reviewed case.
 */
@Tag("integration")
class JdbcBindingPostgresTest
{
	private static final Path EXCEPTIONS = Path.of("src", "test", "resources", "db", "jdbc-binding-exceptions.txt");
	
	@Test
	@DisplayName("every JDBC accessor fits the PostgreSQL type of its parameter or column")
	void everyAccessorFitsTheType() throws Exception
	{
		L2DatabaseFactory factory = WorldDatabase.start();
		List<JdbcCallSites.Site> sites =
				JdbcCallSites.scan(Path.of("src/main/java"));
		
		SortedMap<String, String> failures = new TreeMap<String, String>();
		int checked = 0;
		try (Connection connection = factory.getPoolConnection())
		{
			for (JdbcCallSites.Site site : sites)
			{
				String prefix = site.location().replaceAll(":\\d+$", "") + ": " + site.sql().replaceAll("\\s+", " ").strip() + ": ";
				try (PreparedStatement statement = connection.prepareStatement(site.sql()))
				{
					checked++;
					for (String problem : problems(site, statement.getParameterMetaData(), statement.getMetaData()))
					{
						failures.put(prefix + problem, site.location());
					}
				}
				catch (SQLException e)
				{
					// a statement that does not prepare is reported by GameServerStatementsPostgresTest
				}
			}
		}
		
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target", "jdbc-binding-check.txt"),
				("statements checked: " + checked + System.lineSeparator() + String.join(System.lineSeparator(),
						failures.keySet())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		
		assertThat(checked).as("statements with known SQL found in the sources").isGreaterThan(200);
		assertThat(failures.keySet()).as("JDBC accessors that do not fit the PostgreSQL type, see target/jdbc-binding-check.txt")
				.containsExactlyInAnyOrderElementsOf(exceptions());
	}
	
	private static List<String> problems(JdbcCallSites.Site site, ParameterMetaData parameters, ResultSetMetaData columns)
			throws SQLException
	{
		List<String> problems = new ArrayList<String>();
		for (Map.Entry<Integer, String> setter : site.setters().entrySet())
		{
			int index = setter.getKey();
			if (index > parameters.getParameterCount())
			{
				problems.add(setter.getValue() + "(" + index + ") but the statement has " + parameters.getParameterCount()
						+ " parameters");
				continue;
			}
			String error = JdbcTypeRules.check(setter.getValue(), parameters.getParameterTypeName(index));
			if (error != null)
				problems.add("parameter " + index + ": " + error);
		}
		
		Map<String, String> byName = new HashMap<String, String>();
		int count = columns == null ? 0 : columns.getColumnCount();
		for (int i = 1; i <= count; i++)
			byName.put(columns.getColumnLabel(i).toLowerCase(Locale.ROOT), columns.getColumnTypeName(i));
		
		for (Map.Entry<String, String> getter : site.namedGetters().entrySet())
		{
			String type = byName.get(getter.getKey().toLowerCase(Locale.ROOT));
			if (type == null)
			{
				problems.add(getter.getValue() + "(\"" + getter.getKey() + "\"): no such result column");
				continue;
			}
			String error = JdbcTypeRules.check(getter.getValue(), type);
			if (error != null)
				problems.add("column " + getter.getKey() + ": " + error);
		}
		
		for (Map.Entry<Integer, String> getter : site.indexedGetters().entrySet())
		{
			int index = getter.getKey();
			if (index > count)
			{
				problems.add(getter.getValue() + "(" + index + ") but the result has " + count + " columns");
				continue;
			}
			String error = JdbcTypeRules.check(getter.getValue(), columns.getColumnTypeName(index));
			if (error != null)
				problems.add("column " + index + ": " + error);
		}
		return problems;
	}
	
	private static List<String> exceptions() throws IOException
	{
		List<String> exceptions = new ArrayList<String>();
		if (Files.exists(EXCEPTIONS))
		{
			for (String line : Files.readAllLines(EXCEPTIONS))
			{
				String entry = line.strip();
				if (!entry.isEmpty() && !entry.startsWith("#"))
					exceptions.add(entry);
			}
		}
		return exceptions;
	}
}
