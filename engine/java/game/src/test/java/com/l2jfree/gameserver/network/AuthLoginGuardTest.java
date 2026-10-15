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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthLoginGuardTest
{
	@Test
	@DisplayName("a second AuthLogin cannot claim an account that is already waiting or in the game")
	void secondClaimIsRejected()
	{
		assertThat(AuthLoginGuard.canClaim(false, false)).isTrue();
		assertThat(AuthLoginGuard.canClaim(true, false)).isFalse();
		assertThat(AuthLoginGuard.canClaim(false, true)).isFalse();
	}
	
	@Test
	@DisplayName("a login-server answer applies only to the session key that was checked")
	void answerMustEchoTheKeyThatWasSent()
	{
		assertThat(AuthLoginGuard.sameKey(1, 2, 3, 4, true, 1, 2, 3, 4)).isTrue();
		assertThat(AuthLoginGuard.sameKey(1, 2, 3, 4, true, 9, 2, 3, 4)).isFalse();
		assertThat(AuthLoginGuard.sameKey(1, 2, 3, 4, false, 1, 2, 3, 4)).isFalse();
	}
}
