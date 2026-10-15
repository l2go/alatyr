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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Deployment settings: values an operator sets in the environment of the process.
 * <p>
 * A setting named {@code DB_URL} is the environment variable {@code L2JFREE_DB_URL}. A secret is never an
 * environment value: the variable {@code L2JFREE_<name>_FILE} names a file that holds it. A deployment setting
 * wins over the properties files.
 */
public final class Deployment
{
	/** Prefix of every deployment variable. */
	public static final String PREFIX = "L2JFREE_";

	/** Suffix of a variable that names a file holding a secret. */
	public static final String FILE_SUFFIX = "_FILE";

	private Deployment()
	{
	}

	/**
	 * @param name the setting name without the prefix, for example {@code DB_URL}
	 * @param fallback the value to use when the variable is not set or is empty
	 * @return the value of {@code L2JFREE_<name>}, or the fallback
	 */
	public static String value(String name, String fallback)
	{
		return value(System::getenv, name, fallback);
	}

	/**
	 * @param name the setting name without the prefix, for example {@code LOGIN_DB_PASSWORD}
	 * @param fallback the value to use when neither variable is set
	 * @return the trimmed content of the file named by {@code L2JFREE_<name>_FILE}, else the value of
	 *         {@code L2JFREE_<name>}, else the fallback
	 * @throws IllegalStateException when the file variable is set and the file cannot be read or is empty
	 */
	public static String secret(String name, String fallback)
	{
		return secret(System::getenv, name, fallback);
	}

	static String value(Function<String, String> environment, String name, String fallback)
	{
		final String value = environment.apply(PREFIX + name);

		return value == null || value.isEmpty() ? fallback : value;
	}

	static String secret(Function<String, String> environment, String name, String fallback)
	{
		final String variable = PREFIX + name + FILE_SUFFIX;
		final String file = environment.apply(variable);

		if (file == null || file.isEmpty())
			return value(environment, name, fallback);

		final String content;
		try
		{
			content = Files.readString(Paths.get(file), StandardCharsets.UTF_8).trim();
		}
		catch (IOException | RuntimeException e)
		{
			throw new IllegalStateException(variable + " names a file that cannot be read: " + file, e);
		}

		if (content.isEmpty())
			throw new IllegalStateException(variable + " names an empty file: " + file);

		return content;
	}
}
