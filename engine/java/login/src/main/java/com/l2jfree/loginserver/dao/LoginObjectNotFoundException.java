/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.loginserver.dao;

/** Indicates that a requested account or registered game server does not exist. */
public class LoginObjectNotFoundException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	public LoginObjectNotFoundException(String entity, Object id)
	{
		super(entity + " not found: " + id);
	}
}
