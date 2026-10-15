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
package com.l2jfree.loginserver.services.exception;

/**
 * Exception for exception during account modification
 * 
 */
public class AccountBannedException extends Exception
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 2448159234999935143L;
	
	/**
	 * Default constructor
	 */
	public AccountBannedException()
	{
		super();
	}
	
	/**
	 * constructor with reason
	 */
	public AccountBannedException(String accountName)
	{
		super("Account " + accountName + " is banned.");
	}
	
	/**
	 * Copy constructor
	 */
	public AccountBannedException(Throwable e)
	{
		super(e);
	}
	
	/**
	 * Copy constructor
	 */
	public AccountBannedException(String accountName, Throwable e)
	{
		super("Account " + accountName + " is banned.", e);
	}
	
}
