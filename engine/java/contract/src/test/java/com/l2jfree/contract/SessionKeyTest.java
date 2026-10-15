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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionKeyTest
{
	private static final SessionKey KEY = new SessionKey(1, 2, 3, 4);
	
	@Test
	void sameKeyMatchesWithAndWithoutLicence()
	{
		assertThat(KEY.matches(new SessionKey(1, 2, 3, 4), true)).isTrue();
		assertThat(KEY.matches(new SessionKey(1, 2, 3, 4), false)).isTrue();
	}
	
	@Test
	void loginPairIsOnlyComparedWhenTheLicenceWasShown()
	{
		SessionKey otherLoginPair = new SessionKey(9, 9, 3, 4);
		
		assertThat(KEY.matches(otherLoginPair, true)).isFalse();
		assertThat(KEY.matches(otherLoginPair, false)).isTrue();
	}
	
	@Test
	void playPairAlwaysDecides()
	{
		assertThat(KEY.matches(new SessionKey(1, 2, 3, 9), true)).isFalse();
		assertThat(KEY.matches(new SessionKey(1, 2, 9, 4), false)).isFalse();
	}
	
	@Test
	void theTextOfAKeyDoesNotContainTheKey()
	{
		assertThat(new SessionKey(1234567, 2345678, 3456789, 4567890).toString()).doesNotContain("1234567")
				.doesNotContain("2345678").doesNotContain("3456789").doesNotContain("4567890");
	}
	
	@Test
	void nothingMatchesNull()
	{
		assertThat(KEY.matches(null, false)).isFalse();
	}
}
