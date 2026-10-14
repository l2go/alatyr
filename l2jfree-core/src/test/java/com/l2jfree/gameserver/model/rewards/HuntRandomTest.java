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
package com.l2jfree.gameserver.model.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HuntRandomTest
{
	@Test
	void seedAndSplitMixMatchIndependentUnsignedVectors()
	{
		// SHA-256 of length-prefixed UTF-8 strings followed by big-endian u64s.
		assertThat(HuntRandom.seed("test-world", 1, 0)).isEqualTo(0xa8970fd33176f232L);
		assertThat(HuntRandom.roll(0xa8970fd33176f232L, 0)).isEqualTo(0x6a2853f8b2f2397bL);
		assertThat(HuntRandom.seed("test-world", 1, 1)).isEqualTo(0xba0aa6efba37908dL);
		assertThat(HuntRandom.roll(0xba0aa6efba37908dL, 1)).isEqualTo(0x3e55edc3f8c83c9dL);
		assertThat(HuntRandom.seed("test-world", 2, 0)).isEqualTo(0x192f4e3153ae6c9dL);
		assertThat(HuntRandom.roll(0x192f4e3153ae6c9dL, 0)).isEqualTo(0xba8e228155ae27feL);
		assertThat(HuntRandom.seed("\u043c\u0438\u0440", -1L, 2)).isEqualTo(0xe25067c68216906fL);
		assertThat(HuntRandom.roll(0xe25067c68216906fL, 2)).isEqualTo(0xefa7761f9aeb58e2L);
		assertThat(HuntRandom.roll(0, 0)).isEqualTo(0xe220a8397b1dcdafL);
	}

	@Test
	void rangeUsesTheUnsignedHighProduct()
	{
		assertThat(new HuntRandom("test-world", 1).nextInt(1000)).isEqualTo(414);
		assertThat(new HuntRandom("test-world", 2).nextInt(1000)).isEqualTo(728);
		HuntRandom random = new HuntRandom("test-world", 1);
		assertThat(random.nextLong()).isEqualTo(0x6a2853f8b2f2397bL);
		assertThat(random.nextLong()).isEqualTo(0x3e55edc3f8c83c9dL);
	}
}
