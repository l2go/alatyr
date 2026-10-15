/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps a second server from starting on the same world database.
 * <p>
 * The lock is a PostgreSQL advisory lock held by a session that stays open for as long as the server runs. The session
 * is a connection of its own, outside the pool, so the pool can neither lend it nor retire it. When the server stops or
 * crashes, the database releases the lock by itself.
 */
public final class WorldLock implements AutoCloseable
{
	private static final Logger _log = LoggerFactory.getLogger(WorldLock.class);
	
	/** The key of the lock: the bytes of "L2JW". */
	private static final long KEY = 0x4C324A57L;
	
	private final Connection session;
	
	private WorldLock(Connection session)
	{
		this.session = session;
	}
	
	/**
	 * @throws IllegalStateException if another server holds the lock or the database cannot be reached
	 */
	public static WorldLock acquire(String url, String user, String password)
	{
		try
		{
			Connection session = DriverManager.getConnection(url, user, password);
			boolean locked = false;
			try (PreparedStatement statement = session.prepareStatement("SELECT pg_try_advisory_lock(?)"))
			{
				statement.setLong(1, KEY);
				try (ResultSet result = statement.executeQuery())
				{
					locked = result.next() && result.getBoolean(1);
				}
			}
			finally
			{
				if (!locked)
				{
					session.close();
				}
			}
			
			if (!locked)
			{
				throw new IllegalStateException("Another server is running on this world database. Stop it first.");
			}
			return new WorldLock(session);
		}
		catch (SQLException e)
		{
			throw new IllegalStateException("Cannot lock the world database", e);
		}
	}
	
	@Override
	public void close()
	{
		try
		{
			session.close();
		}
		catch (SQLException e)
		{
			_log.warn("Cannot release the world database lock", e);
		}
	}
}
