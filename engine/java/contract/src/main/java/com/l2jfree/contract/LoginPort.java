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
package com.l2jfree.contract;

/**
 * What the world asks of the login module. The login module implements it.
 */
public interface LoginPort
{
	/**
	 * Admits an account that presents a session key. The call is atomic: when it admits, the account counts as
	 * in the world and its login session is closed, so a second admission of the same account is refused.
	 *
	 * @param account the account name
	 * @param key     the key the client presented to the world
	 * @return the decision, with the address the client used for the login
	 */
	AdmissionResult admit(String account, SessionKey key);
	
	/**
	 * Tells the login module that the account is no longer in the world, so it may log in again.
	 */
	void leave(String account);
	
	/**
	 * Sets the access level of an account. Zero is a normal account, a negative level is a ban, and a level at
	 * or above the GM threshold is a game master.
	 */
	void changeAccessLevel(String account, int level);
}
