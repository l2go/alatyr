/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao;

/** Runtime wrapper for database failures in the login server. */
public class LoginDataAccessException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public LoginDataAccessException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
