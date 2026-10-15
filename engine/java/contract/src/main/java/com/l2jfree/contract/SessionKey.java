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
 * The two key pairs of a play session. The login module creates them, the client carries them to the world, and
 * the world hands them back for admission.
 *
 * @param loginOk1 first half of the login-ok pair
 * @param loginOk2 second half of the login-ok pair
 * @param playOk1  first half of the play-ok pair
 * @param playOk2  second half of the play-ok pair
 */
public record SessionKey(int loginOk1, int loginOk2, int playOk1, int playOk2)
{
	/**
	 * @param licenceShown whether the login module showed the licence. When it did not, the client has no
	 *                     login-ok pair, so only the play-ok pair is compared.
	 * @return true when {@code other} opens the same play session as this key
	 */
	public boolean matches(SessionKey other, boolean licenceShown)
	{
		if (other == null)
			return false;
		
		if (playOk1 != other.playOk1 || playOk2 != other.playOk2)
			return false;
		
		return !licenceShown || (loginOk1 == other.loginOk1 && loginOk2 == other.loginOk2);
	}
	
	/**
	 * @return a text that does not contain the key: the key opens a play session, so it must not reach a log
	 */
	@Override
	public String toString()
	{
		return "SessionKey[redacted]";
	}
}
