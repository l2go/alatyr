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
package com.l2jfree.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The count of classes that reach the database directly does not grow.
 * <p>
 * The world module keeps its SQL behind repositories in {@code ..persistence..} where a domain has one, and the rest of
 * the code still calls {@code java.sql} or {@code L2DatabaseFactory} itself. The classes that do are listed in
 * {@code jdbc-outside-persistence.txt}. A class that is not on the list fails the build, so no new direct access
 * appears, and a class on the list that no longer reaches the database fails it too, so the list only shrinks and the
 * move behind repositories is visible.
 */
@Tag("architecture")
class DirectDatabaseAccessRatchetTest
{
	private static final String POOL = "com.l2jfree.L2DatabaseFactory";
	
	private static JavaClasses imported;
	
	@BeforeAll
	static void importClasses()
	{
		imported = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.l2jfree.gameserver");
	}
	
	@Test
	void noClassReachesTheDatabaseDirectlyThatIsNotListed() throws IOException
	{
		SortedSet<String> actual = new TreeSet<String>();
		for (JavaClass javaClass : imported)
		{
			if (javaClass.getPackageName().contains(".persistence"))
				continue;
			
			boolean direct = javaClass.getDirectDependenciesFromSelf().stream().anyMatch(dependency -> {
				JavaClass target = dependency.getTargetClass();
				return target.getPackageName().startsWith("java.sql") || target.getFullName().equals(POOL);
			});
			if (direct)
				actual.add(javaClass.getName().replaceAll("\\$.*", ""));
		}
		
		Files.createDirectories(Path.of("target"));
		Files.write(Path.of("target", "jdbc-outside-persistence.txt"), actual, StandardCharsets.UTF_8);
		
		assertThat(imported).as("classes of the world module").isNotEmpty();
		List<String> listed = listed();
		
		SortedSet<String> added = new TreeSet<String>(actual);
		added.removeAll(listed);
		SortedSet<String> gone = new TreeSet<String>(listed);
		gone.removeAll(actual);
		
		assertThat(added).as("classes that reach the database directly and are not in jdbc-outside-persistence.txt "
				+ "(use a repository; the full list is in target/jdbc-outside-persistence.txt)").isEmpty();
		assertThat(gone).as("classes in jdbc-outside-persistence.txt that no longer reach the database directly: "
				+ "remove them from the list").isEmpty();
	}
	
	private static List<String> listed() throws IOException
	{
		try (InputStream input = DirectDatabaseAccessRatchetTest.class.getResourceAsStream("/jdbc-outside-persistence.txt"))
		{
			assertThat(input).as("jdbc-outside-persistence.txt").isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
		}
	}
}
