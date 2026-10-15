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
package com.l2jfree;

/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.l2jfree.loginserver.dao.AccountsDAO;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.impl.AccountsDAOJdbc;
import com.l2jfree.loginserver.db.LoginDataSource;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.sql.SchemaMigration;

/**
 * 
 * Object registry for L2 LS.
 * 
 * The registry store singleton and is able to act as a factory.
 * The login persistence service graph is initialized here.
 * 
 * There is no risk to call the load method more than one time.
 * The first call initialize all singleton by IoC mechanism.
 * 
 */
public class L2Registry
{
	private static LoginDataSource __loginDataSource;
	private static AccountsServices __accountsServices;
	
	/**
	 * Initialize the login data source and its service graph once.
	 */
	public static synchronized void loadRegistry()
	{
		if (__loginDataSource != null)
		{
			return;
		}
		try
		{
			LoginDataSource loginDataSource = new LoginDataSource();
			try
			{
				SchemaMigration.migrate(loginDataSource.getDataSource(), LoginDataSource.SCHEMA, "classpath:db/login");
			}
			catch (RuntimeException e)
			{
				loginDataSource.close();
				throw e;
			}
			JdbcTransactions transactions = new JdbcTransactions(loginDataSource.getDataSource());
			AccountsDAO accountsDAO = new AccountsDAOJdbc(transactions);
			AccountsServices accountsServices = new AccountsServices();
			accountsServices.setAccountsDAO(accountsDAO);

			__loginDataSource = loginDataSource;
			__accountsServices = accountsServices;
			Runtime.getRuntime().addShutdownHook(new Thread(loginDataSource::close, "login-database-pool-shutdown"));
		}
		catch (RuntimeException e)
		{
			throw new IllegalStateException("Unable to initialize login registry", e);
		}
	}

	public static AccountsServices getAccountsServices()
	{
		ensureInitialized();
		return __accountsServices;
	}

	public static DataSource getDataSource()
	{
		ensureInitialized();
		return __loginDataSource.getDataSource();
	}

	private static void ensureInitialized()
	{
		if (__loginDataSource == null)
		{
			throw new IllegalStateException("Login registry has not been initialized");
		}
	}
	
	// =========================================================
	// Data Field
	private static L2Registry _instance;
	
	// =========================================================
	// Constructor
	private L2Registry()
	{
	}
	
	// =========================================================
	// Property - Public
	public static L2Registry getInstance()
	{
		if (_instance == null)
		{
			_instance = new L2Registry();
		}
		return _instance;
	}
	
	/**
	 * if con is not null, return the same connection
	 * dev have to close it !
	 * @param con
	 * @return
	 */
	public static Connection getConnection(Connection con)
	{
		if (con == null)
		{
			try
			{
				con = getDataSource().getConnection();
			}
			catch (SQLException e)
			{
				throw new IllegalStateException("Unable to retrieve login database connection", e);
			}
		}
		return con;
	}
	
	public int getBusyConnectionCount() throws SQLException
	{
		return __loginDataSource.getBusyConnectionCount();
	}

	public int getIdleConnectionCount() throws SQLException
	{
		return __loginDataSource.getIdleConnectionCount();
	}

	public String getConnectionPoolStatus()
	{
		ensureInitialized();
		return __loginDataSource.getPoolStatus();
	}
	
}
