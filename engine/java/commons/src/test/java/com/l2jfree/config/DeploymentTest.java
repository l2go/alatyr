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
package com.l2jfree.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The deployment settings, tested against a map instead of the real environment.
 */
class DeploymentTest
{
	@TempDir
	Path directory;
	
	private final Map<String, String> environment = new HashMap<>();
	
	private final Function<String, String> lookup = environment::get;
	
	@Test
	@DisplayName("a value comes from L2JFREE_<name>")
	void valueComesFromTheEnvironment()
	{
		environment.put("L2JFREE_DB_URL", "jdbc:postgresql://db/l2jfree");
		
		assertThat(Deployment.value(lookup, "DB_URL", "jdbc:default")).isEqualTo("jdbc:postgresql://db/l2jfree");
	}
	
	@Test
	@DisplayName("an unset variable gives the fallback")
	void unsetValueFallsBack()
	{
		assertThat(Deployment.value(lookup, "BIND", "0.0.0.0")).isEqualTo("0.0.0.0");
	}
	
	@Test
	@DisplayName("an empty variable gives the fallback")
	void emptyValueFallsBack()
	{
		environment.put("L2JFREE_BIND", "");
		
		assertThat(Deployment.value(lookup, "BIND", "0.0.0.0")).isEqualTo("0.0.0.0");
	}
	
	@Test
	@DisplayName("a variable without the prefix is not read")
	void prefixIsRequired()
	{
		environment.put("BIND", "10.0.0.1");
		
		assertThat(Deployment.value(lookup, "BIND", "0.0.0.0")).isEqualTo("0.0.0.0");
	}
	
	@Test
	@DisplayName("a secret is the trimmed content of the file named by L2JFREE_<name>_FILE")
	void secretIsReadFromTheFileAndTrimmed() throws IOException
	{
		final Path file = Files.writeString(directory.resolve("password"), "  s3cret\r\n");
		environment.put("L2JFREE_LOGIN_DB_PASSWORD_FILE", file.toString());
		environment.put("L2JFREE_LOGIN_DB_PASSWORD", "plain");
		
		assertThat(Deployment.secret(lookup, "LOGIN_DB_PASSWORD", "default")).isEqualTo("s3cret");
	}
	
	@Test
	@DisplayName("a missing secret file fails and names the variable")
	void missingSecretFileFails()
	{
		final Path file = directory.resolve("absent");
		environment.put("L2JFREE_WORLD_DB_PASSWORD_FILE", file.toString());
		
		assertThatThrownBy(() -> Deployment.secret(lookup, "WORLD_DB_PASSWORD", "default"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("L2JFREE_WORLD_DB_PASSWORD_FILE")
				.hasMessageContaining(file.toString());
	}
	
	@Test
	@DisplayName("an empty secret file fails and names the variable")
	void emptySecretFileFails() throws IOException
	{
		final Path file = Files.writeString(directory.resolve("empty"), " \n");
		environment.put("L2JFREE_WORLD_DB_PASSWORD_FILE", file.toString());
		
		assertThatThrownBy(() -> Deployment.secret(lookup, "WORLD_DB_PASSWORD", "default"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("L2JFREE_WORLD_DB_PASSWORD_FILE");
	}
	
	@Test
	@DisplayName("without a file variable a secret falls back to the plain variable")
	void secretFallsBackToThePlainVariable()
	{
		environment.put("L2JFREE_LOGIN_DB_PASSWORD", "plain");
		
		assertThat(Deployment.secret(lookup, "LOGIN_DB_PASSWORD", "default")).isEqualTo("plain");
	}
	
	@Test
	@DisplayName("an empty file variable is treated as unset")
	void emptyFileVariableIsUnset()
	{
		environment.put("L2JFREE_LOGIN_DB_PASSWORD_FILE", "");
		environment.put("L2JFREE_LOGIN_DB_PASSWORD", "plain");
		
		assertThat(Deployment.secret(lookup, "LOGIN_DB_PASSWORD", "default")).isEqualTo("plain");
	}
	
	@Test
	@DisplayName("without either variable a secret falls back to the default")
	void secretFallsBackToTheDefault()
	{
		assertThat(Deployment.secret(lookup, "LOGIN_DB_PASSWORD", "default")).isEqualTo("default");
	}
	
	@Test
	@DisplayName("the public methods read the process environment and honor the fallback")
	void publicMethodsUseTheProcessEnvironment()
	{
		assertThat(Deployment.value("NO_SUCH_SETTING_FOR_THIS_TEST", "fallback")).isEqualTo("fallback");
		assertThat(Deployment.secret("NO_SUCH_SECRET_FOR_THIS_TEST", "fallback")).isEqualTo("fallback");
	}
}
