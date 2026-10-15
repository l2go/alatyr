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
package com.l2jfree.gameserver.network;

/**
 * Decides which game client may wait for a login-server answer, and which answer belongs to that client.
 * The login-server packet carries the session key that was checked. A success flag alone is not enough:
 * the waiter slot used to be overwritten, and the success was applied to whoever was there.
 */
public final class AuthLoginGuard
{
	private AuthLoginGuard()
	{
	}
	
	public static boolean canClaim(boolean alreadyWaiting, boolean alreadyInGame)
	{
		return !alreadyWaiting && !alreadyInGame;
	}
	
	public static boolean sameKey(int sentPlay1, int sentPlay2, int sentLogin1, int sentLogin2, boolean echoed,
			int echoPlay1, int echoPlay2, int echoLogin1, int echoLogin2)
	{
		return echoed && sentPlay1 == echoPlay1 && sentPlay2 == echoPlay2 && sentLogin1 == echoLogin1
				&& sentLogin2 == echoLogin2;
	}
}
