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
 * What the login module asks of the world. The world module implements it.
 */
public interface WorldPort
{
	/**
	 * @return the current state of the world
	 */
	WorldStatus status();
	
	/**
	 * @param clientIp the address a client used to reach the login port
	 * @return the address the client must use to reach the world, chosen by the subnet configuration
	 */
	String addressFor(String clientIp);
	
	/**
	 * Closes the connection of an account in the world, as a second login of the account asks for.
	 */
	void kick(String account);
	
	/**
	 * Tells the world that a client is about to connect from this address, so that connection filtering lets it in.
	 */
	void expect(String clientIp);
}
