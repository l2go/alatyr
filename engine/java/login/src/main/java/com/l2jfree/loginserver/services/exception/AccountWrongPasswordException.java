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
public class AccountWrongPasswordException extends Exception
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -9080179050086340310L;
	
	/**
	 * Default constructor
	 */
	public AccountWrongPasswordException()
	{
		super();
	}
	
	/**
	 * constructor with reason
	 */
	public AccountWrongPasswordException(String reason)
	{
		super("Wrong password for user " + reason);
	}
	
	/**
	 * Copy constructor
	 */
	public AccountWrongPasswordException(Throwable e)
	{
		super(e);
	}
	
	/**
	 * Copy constructor
	 */
	public AccountWrongPasswordException(String reason, Throwable e)
	{
		super("Wrong password for user " + reason, e);
	}
	
}
