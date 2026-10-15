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
import java.util.Locale;
import java.util.UUID;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL servers for integration tests.
 * <p>
 * Each call creates a new empty database, so test classes never share tables. The server is either external or a
 * Testcontainers container started once per JVM:
 * <ul>
 * <li>{@code -Dl2jfree.test.postgres.url=jdbc:postgresql://host:port} (with optional
 * {@code l2jfree.test.postgres.user} and {@code l2jfree.test.postgres.password}, default {@code postgres}/{@code test})
 * uses an existing PostgreSQL 18 server whose account may create databases. Use this where containers cannot publish
 * ports.</li>
 * <li>Without the property a {@code postgres:18.6} container is started.</li>
 * </ul>
 */
public final class TestDatabases
{
	private TestDatabases()
	{
	}
	
	/** A new, empty PostgreSQL 18 database. */
	public static TestDatabase postgres()
	{
		Server server = PostgresServer.SERVER;
		String name = newName();
		create(server, server.url() + "/postgres", "CREATE DATABASE " + name);
		return new TestDatabase(server.url() + "/" + name, server.user(), server.password());
	}
	
	private static String newName()
	{
		return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toLowerCase(Locale.ROOT);
	}
	
	private static void create(Server server, String adminUrl, String sql)
	{
		try (Connection connection = DriverManager.getConnection(adminUrl, server.user(), server.password());
				Statement statement = connection.createStatement())
		{
			statement.execute(sql);
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Cannot create test database on " + server.url(), e);
		}
	}
	
	private record Server(String url, String user, String password)
	{
	}
	
	private static final class PostgresServer
	{
		static final Server SERVER = start();
		
		private static Server start()
		{
			String external = System.getProperty("l2jfree.test.postgres.url");
			if (external != null && !external.isBlank())
			{
				return new Server(stripTrailingSlash(external),
						System.getProperty("l2jfree.test.postgres.user", "postgres"),
						System.getProperty("l2jfree.test.postgres.password", "test"));
			}
			
			@SuppressWarnings("resource") // lives for the whole JVM, like a Testcontainers singleton container
			PostgreSQLContainer container = new PostgreSQLContainer("postgres:18.6");
			container.start();
			return new Server("jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432),
					container.getUsername(), container.getPassword());
		}
	}
	
	private static String stripTrailingSlash(String url)
	{
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
