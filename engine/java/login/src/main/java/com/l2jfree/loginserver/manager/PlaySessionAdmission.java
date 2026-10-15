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
package com.l2jfree.loginserver.manager;

/**
 * Whether a password check may open a login session, and whether an admission may put the account in the world.
 * The account must be in the world before another login is granted. Leaving it in neither set is the gap.
 * {@link LoginManager} applies both rules under the lock that guards its login-session map, so an account is
 * always in the login-session map, in the world, or in neither, never in both and never half-moved.
 */
public final class PlaySessionAdmission
{
	private PlaySessionAdmission()
	{
	}
	
	public static boolean mayAuthenticate(boolean inWorld, boolean onLoginServer)
	{
		return !inWorld && !onLoginServer;
	}
	
	public static boolean reserveGameServer(boolean alreadyInWorld)
	{
		return !alreadyInWorld;
	}
}
