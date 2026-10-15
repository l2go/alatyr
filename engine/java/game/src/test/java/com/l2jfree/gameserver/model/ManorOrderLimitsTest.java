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
package com.l2jfree.gameserver.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManorOrderLimitsTest
{
	private static final long MAX_ADENA = 99_000_000_000L;
	
	@Test
	@DisplayName("a seed inside the sale limit and the 60 percent to 10x band is stored")
	void bandAndLimitAcceptTheWindowValues()
	{
		assertThat(ManorOrderLimits.allows(8100, 3, 8100, 5, MAX_ADENA)).isTrue();
		assertThat(ManorOrderLimits.allows(1, 50, 8100, 5, MAX_ADENA)).isTrue();
	}
	
	@Test
	@DisplayName("a count or a price the setting window rejects is not stored")
	void overLimitAndOutsideBandAreRejected()
	{
		assertThat(ManorOrderLimits.allows(1_000_000, 1, 8100, 5, MAX_ADENA)).isFalse();
		assertThat(ManorOrderLimits.allows(1, 1, 8100, 5, MAX_ADENA)).isFalse();
		assertThat(ManorOrderLimits.allows(1, 99_000_000_000L, 1, 100, MAX_ADENA)).isFalse();
		assertThat(ManorOrderLimits.allows(-1, 10, 8100, 5, MAX_ADENA)).isFalse();
	}
}
