/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.db;

import javax.sql.DataSource;

import com.l2jfree.loginserver.LoginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/** Owns the login server's JDBC connection pool. */
public final class LoginDataSource implements AutoCloseable
{
	/** The schema this module owns. */
	public static final String SCHEMA = "login";
	
	private final HikariDataSource dataSource;

	public LoginDataSource()
	{
		this(createPoolConfig());
	}

	/** The pool settings of the running server, read from the configuration. */
	static HikariConfig createPoolConfig()
	{
		return poolConfig(LoginConfig.DATABASE_DRIVER, LoginConfig.DATABASE_URL, LoginConfig.DATABASE_LOGIN,
				LoginConfig.DATABASE_PASSWORD, LoginConfig.DATABASE_MAX_CONNECTIONS,
				LoginConfig.DATABASE_MIN_IDLE_CONNECTIONS);
	}

	/**
	 * A PostgreSQL pool whose sessions see the login schema and public (for the citext operators) and send strings
	 * untyped, so that the server infers the column type (citext, inet). The settings are arguments, so a test builds
	 * a pool without touching the static configuration.
	 */
	static HikariConfig poolConfig(String driver, String url, String user, String password, int maxConnections,
			int minIdle)
	{
		HikariConfig pool = new HikariConfig();
		pool.setPoolName("l2jfree-loginserver");
		// the state of the pool (busy, idle, waiting) is read from JMX or from a flight recording
		pool.setRegisterMbeans(true);
		pool.setDriverClassName(driver);
		pool.setJdbcUrl(url);
		pool.setUsername(user);
		pool.setPassword(password);
		pool.setAutoCommit(true);
		int maximumPoolSize = Math.max(1, maxConnections);
		pool.setMaximumPoolSize(maximumPoolSize);
		pool.setMinimumIdle(Math.max(0, Math.min(minIdle, maximumPoolSize)));
		pool.setConnectionTimeout(30_000);
		pool.setValidationTimeout(5_000);
		// Text parameters are sent untyped, so the server types them from the column: a login name is
		// compared as citext (without regard to case) and an address is stored as inet.
		pool.addDataSourceProperty("stringtype", "unspecified");
		// The schema of the module comes first. The public schema stays in the path because the citext type lives there.
		pool.addDataSourceProperty("currentSchema", SCHEMA + ",public");
		pool.addDataSourceProperty("ApplicationName", "l2jfree-login");
		pool.setInitializationFailTimeout(30_000);
		return pool;
	}

	LoginDataSource(HikariConfig pool)
	{
		dataSource = createDataSource(pool);
	}

	private static HikariDataSource createDataSource(HikariConfig pool)
	{
		return new HikariDataSource(pool);
	}

	public DataSource getDataSource()
	{
		return dataSource;
	}

	public int getBusyConnectionCount()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getActiveConnections();
	}

	public int getIdleConnectionCount()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		return pool == null ? 0 : pool.getIdleConnections();
	}

	public String getPoolStatus()
	{
		HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
		if (pool == null)
		{
			return "closed";
		}
		return "active=" + pool.getActiveConnections() + ", idle=" + pool.getIdleConnections() + ", total="
				+ pool.getTotalConnections() + ", waiting=" + pool.getThreadsAwaitingConnection() + ", max="
				+ dataSource.getMaximumPoolSize();
	}

	@Override
	public void close()
	{
		dataSource.close();
	}
}
