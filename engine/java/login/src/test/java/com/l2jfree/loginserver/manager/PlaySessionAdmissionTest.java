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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaySessionAdmissionTest
{
	@Test
	@DisplayName("a second login is refused while the account is on a game server but no longer on the login server")
	void gapBetweenLoginAndGameIsClosed()
	{
		assertThat(PlaySessionAdmission.mayAuthenticate(true, false)).isFalse();
		assertThat(PlaySessionAdmission.mayAuthenticate(false, true)).isFalse();
		assertThat(PlaySessionAdmission.mayAuthenticate(false, false)).isTrue();
	}
	
	@Test
	@DisplayName("a play request reserves the game server only when the account is not already there")
	void playRequestReservesTheGameServer()
	{
		assertThat(PlaySessionAdmission.reserveGameServer(false)).isTrue();
		assertThat(PlaySessionAdmission.reserveGameServer(true)).isFalse();
	}
}
