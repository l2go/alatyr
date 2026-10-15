/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

/** Connection and transaction helpers for login-server JDBC DAOs. */
public final class JdbcTransactions
{
	private final DataSource dataSource;

	public JdbcTransactions(DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	public <T> T withConnection(SqlWork<T> work)
	{
		try (Connection connection = dataSource.getConnection())
		{
			return work.execute(connection);
		}
		catch (SQLException e)
		{
			throw new LoginDataAccessException("Login database operation failed", e);
		}
	}

	public <T> T inTransaction(SqlWork<T> work)
	{
		try (Connection connection = dataSource.getConnection())
		{
			boolean originalAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			boolean restoreAutoCommit = true;
			try
			{
				T result = work.execute(connection);
				connection.commit();
				return result;
			}
			catch (SQLException | RuntimeException | Error e)
			{
				try
				{
					connection.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					e.addSuppressed(rollbackFailure);
					restoreAutoCommit = false;
				}
				if (e instanceof SQLException)
				{
					throw new LoginDataAccessException("Login database transaction failed", e);
				}
				throw e;
			}
			finally
			{
				if (restoreAutoCommit)
				{
					try
					{
						connection.setAutoCommit(originalAutoCommit);
					}
					catch (SQLException ignored)
					{
						// The connection is closed immediately after this method.
					}
				}
			}
		}
		catch (SQLException e)
		{
			throw new LoginDataAccessException("Unable to open login database transaction", e);
		}
	}

	@FunctionalInterface
	public interface SqlWork<T>
	{
		T execute(Connection connection) throws SQLException;
	}
}
