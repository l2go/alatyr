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
 * A snapshot of the world for the server list and for the admission rules of the login module.
 *
 * @param serverId      the number the client shows for this world
 * @param status        the state of the world
 * @param port          the world port the client connects to
 * @param onlinePlayers the number of accounts in the world now
 * @param maxPlayers    the number of accounts the world admits
 * @param ageLimit      the minimum age of an account, 0 for none
 * @param pvp           the PvP flag of the server list
 * @param clock         whether the server list shows the clock
 * @param brackets      whether the server list shows brackets
 * @param testServer    whether the server list marks a test server
 * @param hideName      whether the server list hides the name
 * @param unknownBit    the legacy bit of the server list whose meaning is unknown
 */
public record WorldStatus(int serverId, ServerStatus status, int port, int onlinePlayers, int maxPlayers,
		int ageLimit, boolean pvp, boolean clock, boolean brackets, boolean testServer, boolean hideName,
		boolean unknownBit)
{
	/**
	 * @return true unless the world is down
	 */
	public boolean online()
	{
		return status != ServerStatus.STATUS_DOWN;
	}
}
