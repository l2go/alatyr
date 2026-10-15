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
package com.l2jfree.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

/**
 * A PostgreSQL test database as the deployment prepares it: the schemas exist before the server starts, and the
 * {@code citext} extension is installed in {@code public}. The init script of the compose stack does the same.
 */
public final class PostgresWorld
{
	private PostgresWorld()
	{
	}
	
	public static DataSource dataSource(TestDatabase database)
	{
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(database.jdbcUrl());
		dataSource.setUser(database.user());
		dataSource.setPassword(database.password());
		return dataSource;
	}
	
	/**
	 * Creates the extension and the given schemas, as the init script does before the first start. A failure is an
	 * unchecked exception, so a test setup method needs no {@code throws}.
	 */
	public static void prepare(TestDatabase database, String... schemas)
	{
		try (Connection connection = database.connect(); Statement statement = connection.createStatement())
		{
			statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA public");
			for (String schema : schemas)
				statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Cannot prepare the test database " + database.jdbcUrl(), e);
		}
	}
	
	/** A connection with the session settings of the world: search path world, catalog, public; untyped strings. */
	public static Connection connect(TestDatabase database) throws SQLException
	{
		String url = database.jdbcUrl() + (database.jdbcUrl().contains("?") ? "&" : "?")
				+ "currentSchema=world,catalog,public&stringtype=unspecified";
		return DriverManager.getConnection(url, database.user(), database.password());
	}
}
