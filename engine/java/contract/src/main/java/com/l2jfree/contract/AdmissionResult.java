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
 * The answer of the login module to an admission request.
 *
 * @param admitted   whether the session may enter the world
 * @param clientHost the address the client used to reach the login port, as the world records it for the player
 */
public record AdmissionResult(boolean admitted, String clientHost)
{
	private static final AdmissionResult REFUSED = new AdmissionResult(false, "-1");
	
	public static AdmissionResult admitted(String clientHost)
	{
		return new AdmissionResult(true, clientHost);
	}
	
	public static AdmissionResult refused()
	{
		return REFUSED;
	}
}
