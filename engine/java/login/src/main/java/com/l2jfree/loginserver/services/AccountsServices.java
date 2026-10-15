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
package com.l2jfree.loginserver.services;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.dao.AccountsDAO;
import com.l2jfree.loginserver.dao.LoginDataAccessException;
import com.l2jfree.loginserver.dao.LoginObjectNotFoundException;
import com.l2jfree.loginserver.services.exception.AccountModificationException;
import com.l2jfree.loginserver.security.PasswordHasher;
import com.l2jfree.tools.codec.Base64;

/**
 * Account service to handle account management
 * 
 */
public class AccountsServices
{
	private static Logger _log = LoggerFactory.getLogger(AccountsServices.class);
	
	private AccountsDAO __accDAO = null;
	
	public void setAccountsDAO(AccountsDAO accDAO)
	{
		__accDAO = accDAO;
	}
	
	/**
	 * Add or update an account
	 * @param account
	 * @param password
	 * @param level
	 * @param by
	 * @param bm
	 * @param bd
	 * @param gs
	 * @return the new account
	 * @throws AccountModificationException
	 */
	public Accounts addOrUpdateAccount(String account, String password, String level, int by, int bm, int bd, int gs)
			throws AccountModificationException
	{
		// o initialization
		// ---------------
		Accounts acc = null;
		
		// o update account
		// ---------------
		try
		{
			acc = new Accounts();
			Integer iLevel = new Integer(level);
			acc.setLogin(account);
			acc.setAccessLevel(iLevel);
			acc.setPassword(PasswordHasher.hash(password));
			acc.setBirthYear(Integer.valueOf(by));
			acc.setBirthMonth(Integer.valueOf(bm));
			acc.setBirthDay(Integer.valueOf(bd));
			acc.setLastServerId(Integer.valueOf(gs));
			__accDAO.createOrUpdate(acc);
			if (_log.isDebugEnabled())
				_log.info("Account " + account + " has been updated.");
		}
		catch (NumberFormatException e)
		{
			throw new AccountModificationException("Access level (" + level + ") should be an integer.", e);
		}
		return acc;
	}
	
	public Accounts addOrUpdateAccount(String account, String password, String level)
			throws AccountModificationException
	{
		return addOrUpdateAccount(account, password, level, 1900, 1, 1, 0);
	}
	
	/**
	 * Add or update an account
	 * @param acc
	 * @return the new account
	 * @throws AccountModificationException
	 */
	public Accounts addOrUpdateAccount(Accounts acc) throws AccountModificationException
	{
		// o update account
		// ---------------
		try
		{
			__accDAO.createOrUpdate(acc);
			if (_log.isDebugEnabled())
				_log.info("Account " + acc.getLogin() + " has been updated.");
		}
		catch (LoginDataAccessException e)
		{
			throw new AccountModificationException("Unable to create account.", e);
		}
		return acc;
	}
	
	/**
	 * Writes the columns that the bean gives to an existing account and leaves the others as they are in the table.
	 * A login uses this: the row it read may be stale, and writing it back would undo what changed meanwhile.
	 * @throws AccountModificationException if the account does not exist or cannot be written
	 */
	public void updateGivenColumns(Accounts partial) throws AccountModificationException
	{
		try
		{
			if (!__accDAO.updateGiven(partial))
				throw new AccountModificationException("Account " + partial.getLogin() + " doesn't exist.");
		}
		catch (LoginDataAccessException e)
		{
			throw new AccountModificationException("Unable to update account.", e);
		}
	}

	/**
	 * Replaces the password hash only when the stored hash is still the one that was verified.
	 * A hash written in between is left as it is.
	 * @return false when the account is gone or the stored hash changed
	 */
	public boolean replacePasswordIfUnchanged(String login, String expectedHash, String newHash)
			throws AccountModificationException
	{
		try
		{
			return __accDAO.replacePasswordHash(login, expectedHash, newHash);
		}
		catch (LoginDataAccessException e)
		{
			throw new AccountModificationException("Unable to update account.", e);
		}
	}
	
	/**
	 * Change account level
	 * @param account - the account to update
	 * @param level - the new level
	 * @throws AccountModificationException
	 */
	public void changeAccountLevel(String account, String level) throws AccountModificationException
	{
		try
		{
			Integer iLevel = new Integer(level);
			if (!__accDAO.updateAccessLevel(account, iLevel.intValue()))
			{
				throw new AccountModificationException("Account " + account + " doesn't exist.");
			}
			if (_log.isDebugEnabled())
				_log.debug("Account " + account + " has been updated.");
		}
		catch (NumberFormatException e)
		{
			throw new AccountModificationException("Error : level (" + level + ") should be an integer.", e);
		}
	}
	
	/**
	 * Change account level
	 * @param account - the account to upadte
	 * @param level - the new level
	 * @throws AccountModificationException
	 */
	public void changeAccountLevel(String account, int level) throws AccountModificationException
	{
		if (!__accDAO.updateAccessLevel(account, level))
		{
			throw new AccountModificationException("Account " + account + " doesn't exist.");
		}
		if (_log.isDebugEnabled())
			_log.debug("Account " + account + " has been updated.");
	}
	
	/**
	 * Delete account and all linked objects
	 * @param account
	 * @throws AccountModificationException if account doest not exist
	 */
	public void deleteAccount(String account) throws AccountModificationException
	{
		try
		{
			__accDAO.removeAccountById(account);
		}
		catch (LoginObjectNotFoundException e)
		{
			throw new AccountModificationException("Unable to delete account : " + account
					+ ". This account does not exist.");
		}
	}
	
	/**
	 * Get accounts information
	 *
	 */
	public List<Accounts> getAccountsInfo()
	{
		List<Accounts> list = __accDAO.getAllAccounts();
		return list;
	}
	
	/**
	 * Get account information for a specific id
	 * 
	 */
	public Accounts getAccountById(String id)
	{
		try
		{
			Accounts acc = __accDAO.getAccountById(id);
			return acc;
		}
		catch (LoginObjectNotFoundException e)
		{
			if (_log.isDebugEnabled())
				_log.debug("Account not found in database: " + id, e);
			return null;
		}
	}
	
	public boolean exists(String accountName)
	{
		try
		{
			__accDAO.getAccountById(accountName);
			return true;
		}
		catch (LoginObjectNotFoundException e)
		{
			return false;
		}
	}
}
