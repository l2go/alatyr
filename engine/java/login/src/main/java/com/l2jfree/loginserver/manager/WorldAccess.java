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

import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.loginserver.services.exception.MaintenanceException;
import com.l2jfree.loginserver.services.exception.MaturityException;

/**
 * The rules that decide whether an account may choose the world, from the state of the world alone. The rules
 * hold no state, so a unit test can run them without a login module.
 */
public final class WorldAccess
{
	private WorldAccess()
	{
	}
	
	/**
	 * @param status the state of the world
	 * @param age    the age of the account in years
	 * @param access the access level of the account
	 * @param gmMin  the lowest access level of a game master
	 * @throws MaintenanceException when the world is down, or when it admits game masters only and the account
	 *                              is not one
	 * @throws MaturityException    when the account is younger than the age limit of the world
	 */
	public static void check(WorldStatus status, int age, int access, int gmMin) throws MaintenanceException,
			MaturityException
	{
		if (!status.online())
			throw MaintenanceException.MAINTENANCE;
		
		if (status.status() == ServerStatus.STATUS_GM_ONLY && access < gmMin)
			throw MaintenanceException.MAINTENANCE;
		
		if (age < status.ageLimit())
			throw new MaturityException(age, status.ageLimit());
	}
	
	/**
	 * @param status the state of the world
	 * @param access the access level of the account
	 * @param gmMin  the lowest access level of a game master
	 * @return true when the world has a free place, or when the account is a game master, who always gets in
	 */
	public static boolean hasPlaceFor(WorldStatus status, int access, int gmMin)
	{
		return status.onlinePlayers() < status.maxPlayers() || access >= gmMin;
	}
}
