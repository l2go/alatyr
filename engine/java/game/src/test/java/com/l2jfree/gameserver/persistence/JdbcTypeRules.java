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

import java.util.Map;
import java.util.Set;

/**
 * Which JDBC accessor may bind or read which PostgreSQL type. The rules are strict on purpose: a mismatch that
 * pgjdbc would accept silently (for example {@code getString} on a {@code boolean}, which returns {@code "t"}) is an
 * error here.
 */
final class JdbcTypeRules
{
	private static final Set<String> INTEGERS = Set.of("int2", "int4", "int8");
	private static final Set<String> NUMBERS = Set.of("int2", "int4", "int8", "numeric", "float4", "float8");
	private static final Set<String> TEXT = Set.of("text", "varchar", "bpchar", "citext", "name", "inet");
	private static final Set<String> MOMENTS = Set.of("timestamptz", "timestamp", "date");
	
	/** Accessor name (without set/get) to the types it may be used with. */
	private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
			Map.entry("Int", INTEGERS), Map.entry("Short", INTEGERS), Map.entry("Byte", INTEGERS),
			Map.entry("Long", INTEGERS), Map.entry("Double", NUMBERS), Map.entry("Float", NUMBERS),
			Map.entry("BigDecimal", NUMBERS), Map.entry("Boolean", Set.of("bool")), Map.entry("String", TEXT),
			Map.entry("Timestamp", MOMENTS), Map.entry("Date", MOMENTS), Map.entry("Bytes", Set.of("bytea")));
	
	/** Names pgjdbc reports for identity and serial columns of a result set, mapped to their storage type. */
	private static final Map<String, String> SERIALS = Map.of("smallserial", "int2", "serial", "int4", "bigserial",
			"int8");
	
	/** Accessors that fit any type. */
	private static final Set<String> ANY = Set.of("Object", "Null", "Array");
	
	private JdbcTypeRules()
	{
	}
	
	/** {@code null} when the accessor fits the type, otherwise a description of the mismatch. */
	static String check(String accessor, String postgresType)
	{
		String type = SERIALS.getOrDefault(postgresType, postgresType);
		String kind = accessor.substring(3); // strip set/get
		if (ANY.contains(kind))
		{
			return null;
		}
		Set<String> allowed = ALLOWED.get(kind);
		if (allowed == null)
		{
			return accessor + " is not a supported accessor";
		}
		return allowed.contains(type) ? null : accessor + " on " + type;
	}
}
