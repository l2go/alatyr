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
package com.l2jfree;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

/** Finds libraries that are on the class path in more than one version. */
final class ClassPathConflicts
{
	private ClassPathConflicts()
	{
	}
	
	/**
	 * Library name (the jar name up to its version) to the names of its jars, for every library present in more than
	 * one version. A jar reached through two entries (for example {@code ./*} and {@code .}) counts once, and jars of
	 * one version that differ only by classifier (native transports per platform) are no conflict.
	 */
	static Map<String, List<String>> find(String classPath)
	{
		Set<File> jars = new TreeSet<>();
		for (String entry : classPath.split(File.pathSeparator))
		{
			File file = new File(entry);
			if (file.isDirectory())
			{
				File[] children = file.listFiles();
				if (children != null)
				{
					for (File child : children)
						addJar(jars, child);
				}
			}
			else
				addJar(jars, file);
		}
		
		Map<String, List<String>> libraries = new TreeMap<>();
		for (File jar : jars)
			libraries.computeIfAbsent(libraryName(jar.getName()), name -> new ArrayList<>()).add(jar.getName());
		libraries.values().removeIf(names -> names.stream().map(ClassPathConflicts::version).distinct().count() < 2);
		libraries.values().forEach(Collections::sort);
		return libraries;
	}
	
	private static void addJar(Set<File> jars, File file)
	{
		if (!file.getName().endsWith("jar") || !file.isFile())
			return;
		try
		{
			jars.add(file.getCanonicalFile());
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
	
	/** {@code netty-codec-4.2.18.Final-linux-x86_64.jar} has version {@code 4.2.18.Final}: the first version part. */
	private static String version(String jarName)
	{
		final String base = jarName.endsWith(".jar") ? jarName.substring(0, jarName.length() - 4) : jarName;
		final String name = libraryName(base);
		final String rest = base.length() > name.length() + 1 ? base.substring(name.length() + 1) : "";
		final int end = rest.indexOf('-');
		return end < 0 ? rest : rest.substring(0, end);
	}
	
	/** {@code javolution-5.4.1.jar} is {@code javolution}: the dash-separated parts before the first version part. */
	private static String libraryName(String jarName)
	{
		StringBuilder name = new StringBuilder();
		StringTokenizer parts = new StringTokenizer(jarName, "-");
		parts: while (parts.hasMoreTokens())
		{
			String part = parts.nextToken();
			boolean numberOnly = true;
			for (int i = 0; i < part.length(); i++)
			{
				char c = part.charAt(i);
				if (numberOnly && c == '.')
					break parts;
				numberOnly &= Character.isDigit(c);
			}
			if (name.length() != 0)
				name.append("-");
			name.append(part);
		}
		return name.toString();
	}
}
