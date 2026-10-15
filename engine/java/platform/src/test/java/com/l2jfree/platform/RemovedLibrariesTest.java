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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Libraries that were removed must not come back. The build refuses their artifacts (the enforcer rule of the module
 * pom) and the image check refuses their jar files; these rules read the bytecode, so a library that arrives shaded
 * into another jar or as a source file is refused as well. They allow no violation.
 */
@Tag("architecture")
class RemovedLibrariesTest
{
	private static JavaClasses classes;
	
	@BeforeAll
	static void importClasses()
	{
		classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.l2jfree", "com.sun.script");
	}
	
	private static void notUsed(String reason, String... packages)
	{
		noClasses().should().dependOnClassesThat().resideInAnyPackage(packages).because(reason).check(classes);
	}
	
	@Test
	void ircLibraryIsNotUsed()
	{
		notUsed("the admin IRC bridge and irclib were removed in 2.0", "org.schwering..");
	}
	
	@Test
	void javaScriptsAreNotCompiledAtRuntime()
	{
		notUsed("Java datapack scripts are compiled in CI and shipped as bytecode", "org.eclipse.jdt..",
				"com.sun.script.java..");
	}
	
	@Test
	void commonsLoggingIsNotUsed()
	{
		notUsed("SLF4J with Logback is the only logging API", "org.apache.commons.logging..");
	}
	
	@Test
	void liquibaseIsNotUsed()
	{
		notUsed("Platform 3.0 migrations use Flyway", "liquibase..");
	}
	
	@Test
	void mysqlDriverIsNotUsed()
	{
		notUsed("Platform 3.0 runs on PostgreSQL only", "com.mysql..");
	}
	
	@Test
	void formerNetworkCoreIsNotUsed()
	{
		notUsed("Platform 3.0 runs the client network on Netty (l2jfree-network)", "com.l2jfree.mmocore..");
	}
	
	@Test
	void javolutionAndTroveAreNotUsed()
	{
		notUsed("the JDK collections replaced Javolution and Trove",
				"javolution..", "gnu.trove..");
	}
	
	@Test
	void graalPythonIsNotUsed()
	{
		notUsed("the datapack scripts run on Jython, and a polyglot runtime is not part of the platform",
				"org.graalvm..", "com.oracle.truffle..");
	}
}
