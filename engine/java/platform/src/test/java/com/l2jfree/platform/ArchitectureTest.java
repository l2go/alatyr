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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * The module architecture as executable rules: the login module and the world module know the contract and
 * nothing of each other, and only the platform package knows both.
 * <p>
 * The rules read the class path of this module, which holds the contract, the login module, the world module, and the
 * platform. A rule that selects no class fails, so an empty import cannot pass silently.
 */
@Tag("architecture")
class ArchitectureTest
{
	private static final String LOGIN = "com.l2jfree.loginserver";
	private static final String WORLD = "com.l2jfree.gameserver";
	private static final String CONTRACT = "com.l2jfree.contract";
	private static final String PLATFORM = "com.l2jfree.platform";
	private static final String NETWORK = "com.l2jfree.network";
	
	private static JavaClasses imported;
	
	@BeforeAll
	static void importClasses()
	{
		imported = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("com.l2jfree");
	}
	
	private static boolean inSlice(String packageName, String slice)
	{
		return packageName.equals(slice) || packageName.startsWith(slice + ".");
	}
	
	// ===================================================================================
	
	static final ArchRule LOGIN_DOES_NOT_KNOW_THE_WORLD = noClasses().that().resideInAPackage(LOGIN + "..")
			.should().dependOnClassesThat().resideInAPackage(WORLD + "..")
			.as("The login module does not depend on the world module")
			.because("the two modules know only the contract, so either can be replaced or tested alone");
	
	static final ArchRule WORLD_DOES_NOT_KNOW_THE_LOGIN = noClasses().that().resideInAPackage(WORLD + "..")
			.should().dependOnClassesThat().resideInAPackage(LOGIN + "..")
			.as("The world module does not depend on the login module")
			.because("the two modules know only the contract, so either can be replaced or tested alone");
	
	static final ArchRule CONTRACT_DEPENDS_ON_THE_JDK_ONLY = classes().that().resideInAPackage(CONTRACT + "..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", CONTRACT + "..")
			.as("The contract depends only on the JDK and itself")
			.because("the contract is the one thing both modules share, so it must pull in nothing of either");
	
	static final ArchRule LOGIN_DOES_NOT_USE_THE_WORLD_CONFIGURATION = noClasses().that()
			.resideInAPackage(LOGIN + "..").should().dependOnClassesThat()
			.haveNameMatching("com\\.l2jfree\\.(Config|L2DatabaseFactory)(\\$.*)?")
			.as("The login module does not use the world's Config or L2DatabaseFactory")
			.because("each module owns its configuration and its database role, "
					+ "and the world's classes carry the world's settings");
	
	static final ArchRule ONLY_THE_PLATFORM_KNOWS_BOTH_MODULES = classes().that().resideOutsideOfPackage(PLATFORM + "..")
			.should(notDependOnBothModules())
			.as("Only the platform package depends on both modules")
			.because("the platform wires the two modules to each other, and nothing else may");
	
	static final ArchRule NO_CYCLES_BETWEEN_THE_SLICES = SlicesRuleDefinition.slices().assignedFrom(topLevelSlices())
			.should().beFreeOfCycles().as("The login module, the world module, and the contract have no cycles")
			.because("a cycle between the slices would make the modules depend on each other");
	
	static final ArchRule THE_MODULES_DO_NOT_KNOW_THE_PLATFORM = noClasses().that()
			.resideInAnyPackage(LOGIN + "..", WORLD + "..", CONTRACT + "..").should().dependOnClassesThat()
			.resideInAPackage(PLATFORM + "..").as("The login module, the world module, and the contract do not depend on the platform")
			.because("the platform wires the modules, so a dependency back on it would be a cycle through the launcher");
	
	static final ArchRule THE_NETWORK_CORE_KNOWS_NO_MODULE = noClasses().that().resideInAPackage(NETWORK + "..")
			.should().dependOnClassesThat().resideInAnyPackage(LOGIN + "..", WORLD + "..", CONTRACT + "..", PLATFORM + "..")
			.as("The network core does not depend on the login module, the world module, the contract, or the platform")
			.because("the network core is a reusable transport; the modules use it, never the other way round");
	
	// ===================================================================================
	
	@Test
	@DisplayName("The login module does not depend on the world module")
	void loginDoesNotKnowTheWorld()
	{
		LOGIN_DOES_NOT_KNOW_THE_WORLD.check(imported);
	}
	
	@Test
	@DisplayName("The world module does not depend on the login module")
	void worldDoesNotKnowTheLogin()
	{
		WORLD_DOES_NOT_KNOW_THE_LOGIN.check(imported);
	}
	
	@Test
	@DisplayName("The contract depends only on the JDK and itself")
	void contractDependsOnTheJdkOnly()
	{
		CONTRACT_DEPENDS_ON_THE_JDK_ONLY.check(imported);
	}
	
	@Test
	@DisplayName("The login module does not use the world's Config or L2DatabaseFactory")
	void loginDoesNotUseTheWorldConfiguration()
	{
		LOGIN_DOES_NOT_USE_THE_WORLD_CONFIGURATION.check(imported);
	}
	
	@Test
	@DisplayName("Only the platform package depends on both modules")
	void onlyThePlatformKnowsBothModules()
	{
		ONLY_THE_PLATFORM_KNOWS_BOTH_MODULES.check(imported);
	}
	
	@Test
	@DisplayName("The login module, the world module, and the contract have no cycles")
	void noCyclesBetweenTheSlices()
	{
		NO_CYCLES_BETWEEN_THE_SLICES.check(imported);
	}
	
	@Test
	@DisplayName("The modules do not depend on the platform")
	void theModulesDoNotKnowThePlatform()
	{
		THE_MODULES_DO_NOT_KNOW_THE_PLATFORM.check(imported);
	}
	
	@Test
	@DisplayName("The network core does not depend on any module")
	void theNetworkCoreKnowsNoModule()
	{
		THE_NETWORK_CORE_KNOWS_NO_MODULE.check(imported);
	}
	
	// ===================================================================================
	
	/** A class touches a module when it belongs to it or depends on it. */
	private static ArchCondition<JavaClass> notDependOnBothModules()
	{
		return new ArchCondition<JavaClass>("not depend on both the login module and the world module") {
			@Override
			public void check(JavaClass item, ConditionEvents events)
			{
				final boolean login = touches(item, LOGIN);
				final boolean world = touches(item, WORLD);
				if (login && world)
					events.add(SimpleConditionEvent.violated(item,
							item.getName() + " depends on both the login module and the world module"));
			}
		};
	}
	
	private static boolean touches(JavaClass item, String slice)
	{
		if (inSlice(item.getPackageName(), slice))
			return true;
		
		for (Dependency dependency : item.getDirectDependenciesFromSelf())
			if (inSlice(dependency.getTargetClass().getPackageName(), slice))
				return true;
		
		return false;
	}
	
	private static SliceAssignment topLevelSlices()
	{
		return new SliceAssignment() {
			@Override
			public String getDescription()
			{
				return "the login module, the world module, and the contract";
			}
			
			@Override
			public SliceIdentifier getIdentifierOf(JavaClass javaClass)
			{
				final String packageName = javaClass.getPackageName();
				if (inSlice(packageName, LOGIN))
					return SliceIdentifier.of("login");
				if (inSlice(packageName, WORLD))
					return SliceIdentifier.of("world");
				if (inSlice(packageName, CONTRACT))
					return SliceIdentifier.of("contract");
				return SliceIdentifier.ignore();
			}
		};
	}
}
