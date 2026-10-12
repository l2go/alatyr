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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a configuration file against the defaults directory and the operator directory.
 * <p>
 * The file name is the last element of the path a caller passes. The defaults file is read from the directory
 * named by the system property {@value #DEFAULTS_PROPERTY}, or from the directory of the path as given when the
 * property is not set, so a checkout starts unchanged. When the operator directory is named by
 * {@value #OPERATOR_PROPERTY} and holds a file of the same name, the keys of that file replace the defaults. An
 * operator key that the defaults file does not contain is logged once, because it is usually a typo.
 * <p>
 * The only global state the resolution reads is the two system properties.
 */
final class ConfigOverlay
{
	/** System property: the directory of the shipped, read-only default files. */
	static final String DEFAULTS_PROPERTY = "l2jfree.config.defaults";
	
	/** System property: the directory of the files an operator changes. */
	static final String OPERATOR_PROPERTY = "l2jfree.config.operator";
	
	private static final Logger _log = LoggerFactory.getLogger(ConfigOverlay.class);
	
	/** Operator keys that were already reported, as {@code <file name>:<key>}. */
	private static final Set<String> _reported = ConcurrentHashMap.newKeySet();
	
	private ConfigOverlay()
	{
	}
	
	/**
	 * @param asGiven the path the caller passed
	 * @return the file to read the defaults from, which is {@code asGiven} when no defaults directory applies
	 */
	static File defaultsFile(File asGiven)
	{
		final String directory = property(DEFAULTS_PROPERTY);
		if (directory == null)
			return asGiven;
		
		final File candidate = new File(directory, asGiven.getName());
		return candidate.isFile() ? candidate : asGiven;
	}
	
	/**
	 * @param asGiven the path the caller passed
	 * @return the operator file of the same name, or null when no operator directory is set or it holds no such file
	 */
	static File operatorFile(File asGiven)
	{
		final String directory = property(OPERATOR_PROPERTY);
		if (directory == null)
			return null;
		
		final File candidate = new File(directory, asGiven.getName());
		return candidate.isFile() ? candidate : null;
	}
	
	/**
	 * Loads the defaults file into the target and then overlays the operator file.
	 * 
	 * @param target receives the keys of both files
	 * @param asGiven the path the caller passed
	 * @return the keys of the operator file that the defaults file does not contain, sorted
	 * @throws IOException when the defaults file is missing or a file cannot be read
	 */
	static Set<String> load(Properties target, File asGiven) throws IOException
	{
		final Properties defaults = new Properties();
		read(defaults, defaultsFile(asGiven));
		
		final File operatorFile = operatorFile(asGiven);
		final Properties operator = new Properties();
		if (operatorFile != null)
			read(operator, operatorFile);
		
		final Set<String> unknown = new TreeSet<String>();
		for (String key : operator.stringPropertyNames())
			if (!defaults.containsKey(key))
				unknown.add(key);
		
		defaults.putAll(operator);
		
		for (String key : defaults.stringPropertyNames())
			target.setProperty(key, defaults.getProperty(key));
		
		if (!unknown.isEmpty())
			warn(asGiven.getName(), unknown);
		
		return Collections.unmodifiableSet(unknown);
	}
	
	private static void read(Properties properties, File file) throws IOException
	{
		try (InputStream in = new FileInputStream(file))
		{
			properties.load(in);
		}
	}
	
	private static void warn(String fileName, Set<String> keys)
	{
		for (String key : keys)
			if (_reported.add(fileName + ":" + key))
				_log.warn("Config: operator file '" + fileName + "' sets '" + key
						+ "', which the defaults file does not contain. Check the spelling.");
	}
	
	private static String property(String name)
	{
		final String value = System.getProperty(name);
		return value == null || value.isBlank() ? null : value;
	}
}
