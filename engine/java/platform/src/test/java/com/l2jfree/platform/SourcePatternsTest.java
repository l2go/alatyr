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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Source patterns that bytecode rules cannot see.
 */
class SourcePatternsTest
{
	/** {@code map.remove(key);} directly followed by {@code map.put(key, ...)}. */
	private static final Pattern REMOVE_THEN_PUT =
			Pattern.compile("(\\w+)\\.remove\\(([^;\\n]*?)\\);\\s*\\1\\.put\\(\\2\\s*,");
	
	/**
	 * Javolution's FastMap let a loop over a map remove and re-put the same key. On a JDK map this throws
	 * ConcurrentModificationException, and on a ConcurrentHashMap it can re-append the key behind the iterator forever
	 * (the raid boss save loop on shutdown). {@code put} alone replaces the value in place.
	 */
	@Test
	void mapsAreNotUpdatedByRemoveThenPut() throws IOException
	{
		List<String> found = new ArrayList<String>();
		for (Path file : sources())
		{
			String source = Files.readString(file).replace("\r", "");
			Matcher matcher = REMOVE_THEN_PUT.matcher(source);
			while (matcher.find())
				found.add(file + ":" + lineOf(source, matcher.start()) + " " + matcher.group(1));
		}
		
		assertThat(found).as("remove-then-put of the same key").isEmpty();
	}
	
	/** {@code for (T x : name)}, {@code name.values()}, {@code name.keySet()} or {@code name.entrySet()}. */
	private static final Pattern LOOP = Pattern
			.compile("for\\s*\\(\\s*[\\w<>\\[\\], ?.]+\\s+\\w+\\s*:\\s*(\\w+)(?:\\.(?:values|keySet|entrySet)\\(\\))?\\s*\\)");
	
	/** A change of the collection that the loop walks, by name. */
	private static final String CHANGE = "\\.(?:remove|clear|add|addAll|removeAll|removeIf)\\(";
	
	/** What may follow the change when it ends the loop: a few resets of fields, then {@code break} or {@code return}. */
	private static final Pattern ENDS_THE_LOOP = Pattern.compile("^\\s*(?:\\w+\\s*=\\s*null;\\s*)*(?:break|return)\\b");
	
	/**
	 * Javolution's FastList and FastMap let a loop remove from the collection it walks. On the JDK collections this
	 * throws ConcurrentModificationException at the next step (clan dissolve, quest exit, castle upgrades and spell
	 * targets did). A loop may change its own collection only when the change ends the loop, or when the collection
	 * is a concurrent one. Otherwise walk a copy.
	 */
	@Test
	void collectionsAreNotChangedWhileTheyAreWalked() throws IOException
	{
		List<String> found = new ArrayList<String>();
		for (Path file : sources())
		{
			String source = Files.readString(file).replace("\r", "");
			Matcher loop = LOOP.matcher(source);
			while (loop.find())
			{
				String name = loop.group(1);
				String body = bodyAfter(source, loop.end());
				Matcher change = Pattern.compile("\\b" + name + CHANGE).matcher(body);
				if (!change.find() || isConcurrent(source, name))
					continue;
				
				String after = body.substring(change.end());
				String rest = after.substring(Math.min(after.length(), after.indexOf(';') + 1));
				boolean ends = ENDS_THE_LOOP.matcher(rest).find() || body.contains("return " + name + ".remove(");
				if (!ends)
					found.add(file + ":" + lineOf(source, loop.start()) + " " + name);
			}
		}
		
		assertThat(found).as("loops that change the collection they walk").isEmpty();
	}
	
	/**
	 * A session key and a generated password must come from a generator that cannot be predicted. {@code Rnd} has 48
	 * bits of state, made for the rates of the game: a few outputs reveal the rest.
	 */
	@Test
	void secretsAreNotMadeWithTheGameGenerator() throws IOException
	{
		Pattern secret = Pattern.compile("new SessionKey\\([^;]*[^e]Rnd\\.|generateRandomPassword[^}]*[^e]Rnd\\.");
		List<String> found = new ArrayList<String>();
		for (Path file : sources())
		{
			String source = Files.readString(file).replace("\r", "");
			Matcher matcher = secret.matcher(source);
			while (matcher.find())
				found.add(file + ":" + lineOf(source, matcher.start()));
		}
		
		assertThat(found).as("secrets made with Rnd").isEmpty();
	}
	
	/**
	 * {@code a = ArrayUtils.add(b, x)} with another array than the one assigned: the copy-and-paste error that made
	 * the Tower of Naia build three door lists from the pre-open ids.
	 */
	@Test
	void arraysAreGrownFromThemselves() throws IOException
	{
		Pattern grown = Pattern.compile("(?<![\\w.])(\\w+)\\s*=\\s*ArrayUtils\\.add\\(\\s*(?!\\1\\b)(\\w+)\\s*,");
		List<String> found = new ArrayList<String>();
		for (Path file : sources())
		{
			String source = Files.readString(file).replace("\r", "");
			Matcher matcher = grown.matcher(source);
			while (matcher.find())
				found.add(file + ":" + lineOf(source, matcher.start()) + " " + matcher.group(1) + " from " + matcher.group(2));
		}
		
		assertThat(found).as("arrays grown from another array").isEmpty();
	}
	
	private static boolean isConcurrent(String source, String name)
	{
		Matcher declaration = Pattern.compile("\\b" + name + "\\s*=\\s*[^;]*;").matcher(source);
		while (declaration.find())
			if (declaration.group().matches("(?s).*(Concurrent|CopyOnWrite|setShared).*"))
				return true;
		return false;
	}
	
	/** The block or the single statement after a loop header. */
	private static String bodyAfter(String source, int from)
	{
		int start = from;
		while (Character.isWhitespace(source.charAt(start)))
			start++;
		if (source.charAt(start) != '{')
			return source.substring(start, source.indexOf(';', start) + 1);
		
		int depth = 0;
		for (int i = start; i < source.length(); i++)
		{
			if (source.charAt(i) == '{')
				depth++;
			else if (source.charAt(i) == '}' && --depth == 0)
				return source.substring(start + 1, i);
		}
		return source.substring(start + 1);
	}
	
	private static int lineOf(String source, int offset)
	{
		return source.substring(0, offset).split("\n", -1).length;
	}
	
	private static List<Path> sources() throws IOException
	{
		List<Path> sources = new ArrayList<Path>();
		for (Path root : List.of(Path.of("../l2j-commons/src/main/java"), Path.of("../l2jfree-login/src/main/java"),
				Path.of("../l2jfree-core/src/main/java")))
		{
			assertThat(root).isDirectory();
			try (Stream<Path> files = Files.walk(root))
			{
				files.filter(f -> f.toString().endsWith(".java")).forEach(sources::add);
			}
		}
		return sources;
	}
}
