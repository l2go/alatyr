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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import org.testcontainers.postgresql.PostgreSQLContainer;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;

/**
 * One PostgreSQL 18 for all the integration tests of the world module in a JVM.
 * <p>
 * The first call starts the container, creates the schemas, sets the pool settings in {@link Config}, and applies the
 * migrations and the catalog exactly as the server does at start. Later calls return at once. Tests share the database,
 * so each test uses ids and names of its own and cleans up what it adds.
 */
public final class WorldDatabase
{
	/** The catalog of the authored world. Tests run with the module directory as the working directory. */
	public static final Path CATALOG = Path.of("..", "..", "..", "world", "catalog");
	
	private static PostgreSQLContainer container;
	
	private WorldDatabase()
	{
	}
	
	/** Starts the database once and returns the pool of the world module. */
	public static synchronized L2DatabaseFactory start()
	{
		if (container == null)
		{
			PostgreSQLContainer started = new PostgreSQLContainer("postgres:18.6");
			started.start();
			try (Connection connection = started.createConnection(""); Statement statement = connection.createStatement())
			{
				statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA public");
				statement.execute("CREATE SCHEMA world");
				statement.execute("CREATE SCHEMA catalog");
				statement.execute("CREATE SCHEMA report");
			}
			catch (Exception e)
			{
				started.stop();
				throw new IllegalStateException("Cannot create the schemas of the test database", e);
			}
			
			Config.DATABASE_DRIVER = "org.postgresql.Driver";
			Config.DATABASE_URL = started.getJdbcUrl();
			Config.DATABASE_LOGIN = started.getUsername();
			Config.DATABASE_PASSWORD = started.getPassword();
			Config.DATABASE_MAX_CONNECTIONS = 10;
			Config.DATABASE_MIN_IDLE_CONNECTIONS = 1;
			container = started;
			
			try
			{
				WorldSchemas.prepare(L2DatabaseFactory.getInstance().getDataSource(), CATALOG);
			}
			catch (Exception e)
			{
				throw new IllegalStateException("Cannot prepare the schemas of the test database", e);
			}
		}
		return L2DatabaseFactory.getInstance();
	}
	
	/** Runs a statement as the test database superuser, for example to remove the rows a test added. */
	public static void execute(String sql)
	{
		try (Connection connection = start().getPoolConnection(); Statement statement = connection.createStatement())
		{
			statement.execute(sql);
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
