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
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;

/**
 * Prepares every SQL statement of the game server against the real schema.
 * <p>
 * The test reads the constant pool of every compiled class, takes each string that is an SQL statement, and runs
 * <code>PREPARE</code> on it in a PostgreSQL 18 that has the world schema and the full catalog. A statement that names a
 * table or a column that does not exist, or that uses MySQL syntax, fails the build, and the failure names the class and
 * the statement. The check proves that the names and the syntax are right; the tests of the repositories prove what the
 * statements do.
 * <p>
 * A statement that the compiler concatenates at run time cannot be checked here. Each of those has to be listed in
 * <code>db/dynamic-statements.txt</code> with the reason, so the exceptions are visible and reviewed.
 */
@Tag("integration")
class GameServerStatementsPostgresTest
{
	/** An SQL statement: a verb that starts the string, in capitals, or the lower-case form with its second keyword. */
	private static final Pattern STATEMENT = Pattern.compile(
			"^\\s*(SELECT\\s|INSERT\\s+INTO\\s|UPDATE\\s|DELETE\\s+FROM\\s|WITH\\s)"
					+ "|^\\s*(?i:select\\s.+\\sfrom\\s|insert\\s+into\\s|update\\s+\\S+\\s+set\\s|delete\\s+from\\s)");
	
	/** The marker the compiler puts into the recipe of a string concatenated at run time. */
	private static final char RUNTIME_PART = '\u0001';
	
	/** The compiled classes of the module. Tests run with the module directory as the working directory. */
	private static final Path CLASSES = Path.of("target", "classes");
	
	@Test
	@DisplayName("every SQL statement of the game server prepares against the schema")
	void everyStatementPrepares() throws Exception
	{
		L2DatabaseFactory factory = WorldDatabase.start();
		
		List<String> failures = new ArrayList<String>();
		List<String> dynamic = new ArrayList<String>();
		int checked = 0;
		
		for (Candidate candidate : candidates())
		{
			if (candidate.sql().indexOf(RUNTIME_PART) >= 0)
			{
				dynamic.add(candidate.owner() + ": " + abbreviate(candidate.sql().replace(RUNTIME_PART, '#')));
				continue;
			}
			
			checked++;
			String problem = prepare(factory, candidate.sql());
			if (problem != null)
			{
				failures.add(candidate.owner() + ": " + abbreviate(candidate.sql()) + "\n    -> " + problem);
			}
		}
		
		Set<String> allowed = allowedDynamicStatements();
		List<String> unlisted = dynamic.stream().filter(entry -> allowed.stream().noneMatch(entry::contains)).toList();
		
		report(checked, failures, unlisted);
		
		assertThat(checked).as("statements found in the compiled classes").isGreaterThan(100);
		assertThat(failures).as("statements that do not prepare against the schema").isEmpty();
		assertThat(unlisted).as("statements built at run time that are not in db/dynamic-statements.txt").isEmpty();
	}
	
	/** Writes what the check found next to the build output, so a failed run can be read from the artifact. */
	private static void report(int checked, List<String> failures, List<String> unlisted) throws IOException
	{
		Path report = Path.of("target", "statement-check.txt");
		List<String> lines = new ArrayList<String>();
		lines.add("statements prepared: " + checked);
		lines.add("failures: " + failures.size());
		lines.addAll(failures);
		lines.add("built at run time and not listed: " + unlisted.size());
		lines.addAll(unlisted);
		Files.write(report, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		lines.forEach(System.out::println);
	}
	
	private static String prepare(L2DatabaseFactory factory, String sql)
	{
		String numbered = number(sql);
		try (Connection connection = factory.getPoolConnection(); Statement statement = connection.createStatement())
		{
			statement.execute("PREPARE statement_check AS " + numbered);
			statement.execute("DEALLOCATE statement_check");
			return null;
		}
		catch (SQLException e)
		{
			return e.getMessage().replace('\n', ' ');
		}
	}
	
	/** Replaces each ? that is not inside a quote with $1, $2, and so on. */
	static String number(String sql)
	{
		StringBuilder numbered = new StringBuilder();
		boolean quoted = false;
		int parameter = 0;
		for (int i = 0; i < sql.length(); i++)
		{
			char c = sql.charAt(i);
			if (c == '\'')
			{
				quoted = !quoted;
			}
			if (c == '?' && !quoted)
			{
				numbered.append('$').append(++parameter);
			}
			else
			{
				numbered.append(c);
			}
		}
		return numbered.toString();
	}
	
	private static List<Candidate> candidates() throws IOException
	{
		List<Candidate> candidates = new ArrayList<Candidate>();
		try (Stream<Path> files = Files.walk(CLASSES))
		{
			for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList())
			{
				ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
				String owner = model.thisClass().asInternalName().replace('/', '.');
				Set<String> seen = new TreeSet<String>();
				for (PoolEntry entry : model.constantPool())
				{
					if (entry instanceof StringEntry string && STATEMENT.matcher(string.stringValue()).find()
							&& seen.add(string.stringValue()))
					{
						candidates.add(new Candidate(owner, string.stringValue()));
					}
				}
			}
		}
		return candidates;
	}
	
	private static Set<String> allowedDynamicStatements() throws IOException
	{
		Path file = Path.of("src", "test", "resources", "db", "dynamic-statements.txt");
		Set<String> allowed = new TreeSet<String>();
		if (Files.exists(file))
		{
			for (String line : Files.readAllLines(file))
			{
				String entry = line.strip();
				if (!entry.isEmpty() && !entry.startsWith("#"))
				{
					allowed.add(entry);
				}
			}
		}
		return allowed;
	}
	
	private static String abbreviate(String sql)
	{
		String flat = sql.replaceAll("\\s+", " ").strip();
		return flat.length() <= 220 ? flat : flat.substring(0, 220) + "...";
	}
	
	private record Candidate(String owner, String sql)
	{
	}
}
