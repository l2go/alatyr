/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao.impl;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.AccountsDAO;
import com.l2jfree.loginserver.dao.JdbcTransactions;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;
import com.l2jfree.sql.Sql;

/**
 * JDBC persistence for login.account.
 * <p>
 * The bean keeps the old in-memory shape, and this class converts at the boundary: the last activity is epoch
 * milliseconds in the bean and a {@code timestamptz} in the table, last server id {@code 0} is {@code NULL}, and the
 * three birth date parts are one {@code date}.
 */
public final class AccountsDAOJdbc implements AccountsDAO
{
	private static final String SELECT_COLUMNS = "SELECT name, password_hash, last_active_at, access_level, "
			+ "last_world_id, birthday_on, host(last_ip) AS last_ip FROM account";

	/** The birth date the old schema stored when a part was not given. */
	private static final int DEFAULT_BIRTH_YEAR = 1900;
	private static final int DEFAULT_BIRTH_MONTH = 1;
	private static final int DEFAULT_BIRTH_DAY = 1;

	private final JdbcTransactions transactions;

	public AccountsDAOJdbc(JdbcTransactions transactions)
	{
		this.transactions = transactions;
	}

	@Override
	public List<Accounts> getAllAccounts()
	{
		return transactions.withConnection(connection -> {
			List<Accounts> accounts = new ArrayList<Accounts>();
			try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS);
					ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					accounts.add(readAccount(result));
				}
			}
			return accounts;
		});
	}

	@Override
	public String createAccount(Object object)
	{
		Accounts account = requireAccount(object);
		return transactions.withConnection(connection -> {
			List<Column> columns = givenColumns(account);
			try (PreparedStatement statement = connection.prepareStatement(insert(columns)))
			{
				bind(statement, account, columns);
				statement.executeUpdate();
				return account.getLogin();
			}
		});
	}

	@Override
	public void createOrUpdate(Object object)
	{
		Accounts account = requireAccount(object);
		transactions.withConnection(connection -> {
			upsert(connection, account);
			return null;
		});
	}

	@Override
	public void createOrUpdateAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			for (Object entity : entities)
			{
				upsert(connection, requireAccount(entity));
			}
			return null;
		});
	}

	@Override
	public void update(Object object)
	{
		Accounts account = requireAccount(object);
		transactions.withConnection(connection -> {
			List<Column> columns = new ArrayList<Column>(List.of(Column.values()));
			columns.remove(Column.NAME);
			List<String> assignments = new ArrayList<String>();
			for (Column column : columns)
			{
				assignments.add(column.sqlName + " = " + column.placeholder);
			}
			columns.add(Column.NAME);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE account SET "
					+ String.join(", ", assignments) + " WHERE name = ?"))
			{
				bind(statement, account, columns);
				if (statement.executeUpdate() == 0)
				{
					throw new LoginObjectNotFoundException("Account", account.getLogin());
				}
			}
			return null;
		});
	}

	@Override
	public void removeAccount(Object object)
	{
		removeAccountById(requireAccount(object).getLogin());
	}

	@Override
	public Accounts getAccountById(String id)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE name = ?"))
			{
				statement.setString(1, id);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new LoginObjectNotFoundException("Account", id);
					}
					return readAccount(result);
				}
			}
		});
	}

	@Override
	public void removeAccountById(String login)
	{
		transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM account WHERE name = ?"))
			{
				statement.setString(1, login);
				if (statement.executeUpdate() == 0)
				{
					throw new LoginObjectNotFoundException("Account", login);
				}
			}
			return null;
		});
	}

	@Override
	public void removeAll(Collection<?> entities)
	{
		transactions.inTransaction(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM account WHERE name = ?"))
			{
				for (Object entity : entities)
				{
					statement.setString(1, requireAccount(entity).getLogin());
					statement.addBatch();
				}
				statement.executeBatch();
			}
			return null;
		});
	}

	@Override
	public boolean updateAccessLevel(String login, int accessLevel)
	{
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(
					"UPDATE account SET access_level = ? WHERE name = ?"))
			{
				statement.setInt(1, accessLevel);
				statement.setString(2, login);
				// PostgreSQL counts matched rows, so an unchanged level still reports the account.
				return statement.executeUpdate() > 0;
			}
		});
	}

	@Override
	public boolean updateGiven(Object partial)
	{
		Accounts account = requireAccount(partial);
		List<Column> columns = new ArrayList<Column>(givenColumns(account));
		columns.remove(Column.NAME);
		if (columns.isEmpty())
		{
			return true;
		}
		List<String> assignments = new ArrayList<String>();
		for (Column column : columns)
		{
			assignments.add(column.sqlName + " = " + column.placeholder);
		}
		columns.add(Column.NAME);
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement("UPDATE account SET "
					+ String.join(", ", assignments) + " WHERE name = ?"))
			{
				bind(statement, account, columns);
				return statement.executeUpdate() > 0;
			}
		});
	}

	@Override
	public boolean replacePasswordHash(String login, String expectedHash, String newHash)
	{
		if (login == null || expectedHash == null || newHash == null)
			throw new IllegalArgumentException("Password replacement needs a login and both hashes");
		return transactions.withConnection(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(
					"UPDATE account SET password_hash = ? WHERE name = ? AND password_hash = ?"))
			{
				statement.setString(1, newHash);
				statement.setString(2, login);
				statement.setString(3, expectedHash);
				return statement.executeUpdate() > 0;
			}
		});
	}

	/** Inserts the account, or updates the columns the bean gives; columns it leaves null keep their value. */
	private static void upsert(Connection connection, Accounts account) throws SQLException
	{
		List<Column> columns = givenColumns(account);
		List<String> updates = new ArrayList<String>();
		for (Column column : columns)
		{
			if (column != Column.NAME)
			{
				updates.add(column.sqlName + " = EXCLUDED." + column.sqlName);
			}
		}
		String sql = insert(columns) + " ON CONFLICT (name) DO "
				+ (updates.isEmpty() ? "NOTHING" : "UPDATE SET " + String.join(", ", updates));
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			bind(statement, account, columns);
			statement.executeUpdate();
		}
	}

	private static String insert(List<Column> columns)
	{
		List<String> names = new ArrayList<String>();
		List<String> placeholders = new ArrayList<String>();
		for (Column column : columns)
		{
			names.add(column.sqlName);
			placeholders.add(column.placeholder);
		}
		return "INSERT INTO account (" + String.join(", ", names) + ") VALUES (" + String.join(", ", placeholders)
				+ ")";
	}

	/** The name and every column the bean gives, so that the table defaults apply to the others. */
	private static List<Column> givenColumns(Accounts account)
	{
		List<Column> columns = new ArrayList<Column>();
		for (Column column : Column.values())
		{
			if (column == Column.NAME || column.isGiven(account))
			{
				columns.add(column);
			}
		}
		return columns;
	}

	private static void bind(PreparedStatement statement, Accounts account, List<Column> columns) throws SQLException
	{
		int index = 1;
		for (Column column : columns)
		{
			column.bind(statement, index++, account);
		}
	}

	private static Accounts readAccount(ResultSet result) throws SQLException
	{
		Accounts account = new Accounts();
		account.setLogin(result.getString("name"));
		account.setPassword(result.getString("password_hash"));
		long lastActive = Sql.getMoment(result, "last_active_at");
		account.setLastactive(lastActive == 0 ? null : BigDecimal.valueOf(lastActive));
		account.setAccessLevel(Integer.valueOf(result.getInt("access_level")));
		account.setLastServerId(Integer.valueOf(result.getInt("last_world_id")));
		LocalDate birthday = result.getObject("birthday_on", LocalDate.class);
		account.setBirthYear(Integer.valueOf(birthday.getYear()));
		account.setBirthMonth(Integer.valueOf(birthday.getMonthValue()));
		account.setBirthDay(Integer.valueOf(birthday.getDayOfMonth()));
		account.setLastIp(result.getString("last_ip"));
		return account;
	}

	/** The birth date of the bean; a date that does not exist (February 30) is the default date, as before. */
	private static LocalDate birthday(Accounts account)
	{
		try
		{
			return LocalDate.of(orDefault(account.getBirthYear(), DEFAULT_BIRTH_YEAR),
					orDefault(account.getBirthMonth(), DEFAULT_BIRTH_MONTH),
					orDefault(account.getBirthDay(), DEFAULT_BIRTH_DAY));
		}
		catch (DateTimeException e)
		{
			return LocalDate.of(DEFAULT_BIRTH_YEAR, DEFAULT_BIRTH_MONTH, DEFAULT_BIRTH_DAY);
		}
	}

	private static int orDefault(Integer value, int defaultValue)
	{
		return value == null ? defaultValue : value.intValue();
	}

	/** An address as inet takes it: without the IPv6 zone that {@code InetAddress.getHostAddress()} may append. */
	private static String inet(String address)
	{
		int zone = address.indexOf('%');
		return zone < 0 ? address : address.substring(0, zone);
	}

	private static Accounts requireAccount(Object object)
	{
		if (!(object instanceof Accounts))
		{
			throw new IllegalArgumentException("Expected an Accounts instance");
		}
		Accounts account = (Accounts)object;
		if (account.getLogin() == null)
		{
			throw new IllegalArgumentException("Account login must not be null");
		}
		return account;
	}

	/** The columns of login.account, how each is bound from the bean, and when the bean gives a value for it. */
	private enum Column
	{
		NAME("name", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				return true;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				statement.setString(index, account.getLogin());
			}
		},
		PASSWORD_HASH("password_hash", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				return account.getPassword() != null;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				statement.setString(index, account.getPassword());
			}
		},
		LAST_ACTIVE_AT("last_active_at", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				return account.getLastactive() != null;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				BigDecimal lastActive = account.getLastactive();
				Sql.setMoment(statement, index, lastActive == null ? 0 : lastActive.longValue());
			}
		},
		ACCESS_LEVEL("access_level", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				return account.getAccessLevel() != null;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				statement.setInt(index, orDefault(account.getAccessLevel(), 0));
			}
		},
		LAST_WORLD_ID("last_world_id", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				return account.getLastServerId() != null;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				int lastServerId = orDefault(account.getLastServerId(), 0);
				if (lastServerId == 0)
				{
					statement.setNull(index, Types.SMALLINT);
				}
				else
				{
					statement.setInt(index, lastServerId);
				}
			}
		},
		BIRTHDAY_ON("birthday_on", "?")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				// a partial date is ignored: it would overwrite the stored month and day with the default
				return account.getBirthYear() != null && account.getBirthMonth() != null
						&& account.getBirthDay() != null;
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				statement.setObject(index, birthday(account));
			}
		},
		LAST_IP("last_ip", "CAST(? AS inet)")
		{
			@Override
			boolean isGiven(Accounts account)
			{
				// an empty address is "not given": it cannot be cast to inet
				return account.getLastIp() != null && !account.getLastIp().isBlank();
			}

			@Override
			void bind(PreparedStatement statement, int index, Accounts account) throws SQLException
			{
				String lastIp = account.getLastIp();
				if (lastIp == null)
				{
					statement.setNull(index, Types.VARCHAR);
				}
				else
				{
					statement.setString(index, inet(lastIp));
				}
			}
		};

		final String sqlName;
		final String placeholder;

		Column(String sqlName, String placeholder)
		{
			this.sqlName = sqlName;
			this.placeholder = placeholder;
		}

		abstract boolean isGiven(Accounts account);

		abstract void bind(PreparedStatement statement, int index, Accounts account) throws SQLException;
	}
}
