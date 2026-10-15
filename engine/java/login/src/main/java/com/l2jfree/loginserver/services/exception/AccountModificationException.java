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
public class AccountModificationException extends Exception
{
	/**
	 * Default serialVersion UID
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Default constructor
	 */
	public AccountModificationException()
	{
		super();
	}
	
	/**
	 * constructor with reason
	 */
	public AccountModificationException(String reason)
	{
		super(reason);
	}
	
	/**
	 * Copy constructor
	 */
	public AccountModificationException(Throwable e)
	{
		super(e);
	}
	
	/**
	 * Copy constructor
	 */
	public AccountModificationException(String reason, Throwable e)
	{
		super(reason, e);
	}
	
}
