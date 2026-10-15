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
package com.l2jfree.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Binds and reads {@code timestamptz} columns for code that keeps moments as epoch milliseconds.
 * <p>
 * The game code uses {@code 0} (or a negative value) for "not set" and {@link Long#MAX_VALUE} for "never ends". The
 * database stores {@code NULL} and {@code 'infinity'} for those, so a person reading a table sees real dates.
 */
public final class Sql
{
	private Sql()
	{
	}
	
	/** Binds epoch milliseconds: {@code <= 0} becomes {@code NULL}, {@link Long#MAX_VALUE} becomes {@code infinity}. */
	public static void setMoment(PreparedStatement statement, int index, long epochMillis) throws SQLException
	{
		if (epochMillis <= 0)
		{
			statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
		}
		else
		{
			statement.setObject(index, toMoment(epochMillis), Types.TIMESTAMP_WITH_TIMEZONE);
		}
	}
	
	/** Reads epoch milliseconds: {@code NULL} is {@code 0}, {@code infinity} is {@link Long#MAX_VALUE}. */
	public static long getMoment(ResultSet rs, String column) throws SQLException
	{
		return toEpochMillis(rs.getObject(column, OffsetDateTime.class));
	}
	
	/** Reads epoch milliseconds by column index; see {@link #getMoment(ResultSet, String)}. */
	public static long getMoment(ResultSet rs, int column) throws SQLException
	{
		return toEpochMillis(rs.getObject(column, OffsetDateTime.class));
	}
	
	static OffsetDateTime toMoment(long epochMillis)
	{
		return epochMillis == Long.MAX_VALUE ? OffsetDateTime.MAX
				: OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
	}
	
	static long toEpochMillis(OffsetDateTime moment)
	{
		if (moment == null)
		{
			return 0;
		}
		if (moment.equals(OffsetDateTime.MAX))
		{
			return Long.MAX_VALUE;
		}
		return moment.toInstant().toEpochMilli();
	}
}
