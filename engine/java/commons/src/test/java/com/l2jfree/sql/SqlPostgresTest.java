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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.testing.TestDatabase;
import com.l2jfree.testing.TestDatabases;

@Tag("integration")
class SqlPostgresTest
{
	private static final TestDatabase POSTGRES = TestDatabases.postgres();
	
	@Test
	void momentsRoundTripWithNullAndInfinity() throws Exception
	{
		try (Connection connection = DriverManager.getConnection(POSTGRES.jdbcUrl(), POSTGRES.user(), POSTGRES.password());
				Statement statement = connection.createStatement())
		{
			statement.execute("CREATE TABLE moment (id int PRIMARY KEY, at timestamptz)");
			long[] values = { 0, -1, 1_790_000_000_123L, Long.MAX_VALUE };
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO moment VALUES (?, ?)"))
			{
				for (int i = 0; i < values.length; i++)
				{
					insert.setInt(1, i);
					Sql.setMoment(insert, 2, values[i]);
					insert.execute();
				}
			}
			try (ResultSet rs = statement.executeQuery("SELECT at, at::text FROM moment ORDER BY id"))
			{
				long[] expected = { 0, 0, 1_790_000_000_123L, Long.MAX_VALUE };
				String[] text = { null, null, null, "infinity" };
				for (int i = 0; i < expected.length; i++)
				{
					assertThat(rs.next()).isTrue();
					assertThat(Sql.getMoment(rs, "at")).isEqualTo(expected[i]);
					if (text[i] != null || i < 2)
					{
						assertThat(rs.getString(2)).isEqualTo(text[i]);
					}
				}
			}
		}
	}
}
