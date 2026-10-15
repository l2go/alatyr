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

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;

/** Helpers of the stores: a connection from the pool, and several statements as one transaction. */
final class Transactions
{
	private static final Logger _log = LoggerFactory.getLogger(Transactions.class);
	
	@FunctionalInterface
	interface Work
	{
		void run(Connection con) throws SQLException;
	}
	
	@FunctionalInterface
	interface Query<T>
	{
		T run(Connection con) throws SQLException;
	}
	
	private Transactions()
	{
	}
	
	static void run(Connection con, Work work) throws SQLException
	{
		boolean autoCommit = con.getAutoCommit();
		con.setAutoCommit(false);
		try
		{
			work.run(con);
			con.commit();
		}
		catch (SQLException | RuntimeException e)
		{
			con.rollback();
			throw e;
		}
		finally
		{
			con.setAutoCommit(autoCommit);
		}
	}
	
	/** Runs {@code query} on a pooled connection; on a database error logs {@code what} and returns {@code fallback}. */
	static <T> T withConnection(String what, T fallback, Query<T> query)
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			return query.run(con);
		}
		catch (SQLException e)
		{
			_log.warn("Cannot " + what, e);
			return fallback;
		}
	}
	
	/** Like {@link #withConnection}, for work without a result. @return whether it succeeded */
	static boolean withConnection(String what, Work work)
	{
		return withConnection(what, false, con -> {
			work.run(con);
			return true;
		});
	}
}
