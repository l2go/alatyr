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

public final class MaturityException extends Exception
{
	private static final long serialVersionUID = 6179849705218182298L;
	
	public MaturityException(int accAge, int reqAge)
	{
		super("Account owner is " + accAge + " years old, while you must have " + reqAge
				+ " to login to the game server.");
	}
}
