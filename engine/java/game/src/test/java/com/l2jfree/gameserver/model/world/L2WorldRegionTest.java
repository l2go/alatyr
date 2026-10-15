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
package com.l2jfree.gameserver.model.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class L2WorldRegionTest
{
	@Test
	@DisplayName("an inactive region activates when multiple playables are already present")
	void multiplePlayablesStillStartActivation()
	{
		assertThat(L2WorldRegion.shouldStartActivation(false, true, false)).isTrue();
		assertThat(L2WorldRegion.shouldStartActivation(false, false, false)).isFalse();
		assertThat(L2WorldRegion.shouldStartActivation(false, true, true)).isFalse();
		assertThat(L2WorldRegion.shouldStartActivation(true, true, false)).isFalse();
	}
}
