/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BridgeRefusalsTest
{
	@Test
	void onlyTheRegisteredMessageAndVerbPairsHaveAWorldReason()
	{
		int[][] rows = {
				{ 781, 16, 4, 5, 6 }, { 2193, 3, 7, 8, 13 }, { 1036, 6, 7, 8 },
				{ 422, 2, 7 }, { 129, 2, 7, 4 }, { 350, 17, 7 }, { 279, 1, 7 },
				{ 794, 16, 7 }, { 483, 18, 8 }, { 276, 15, 13 }, { 278, 14, 13 },
				{ 236, 16, 13 }, { 1852, 16, 13 }, { 149, 16, 4 },
				{ 55, 15, 4 }, { 56, 15, 4 }, { 57, 15, 4 }
		};
		for (int id = -1; id <= 2200; id++)
		{
			for (int verb = 0; verb <= 14; verb++)
			{
				int expected = 0;
				for (int[] row : rows)
					if (row[0] == id)
						for (int i = 2; i < row.length; i++)
							if (row[i] == verb)
								expected = row[1];
				assertThat(BridgeRefusals.reason(verb, id)).as("verb %s, message %s", verb, id)
						.isEqualTo(expected);
			}
		}
		assertThat(BridgeRefusals.reason(7, Integer.MAX_VALUE)).isZero();
		assertThat(BridgeRefusals.reason(Integer.MAX_VALUE, 279)).isZero();
	}

	@Test
	void theFirstApplicableMessageWinsInDeliveryOrderNotNumericOrder()
	{
		assertThat(BridgeRefusals.firstReason(7, List.of(483, 350, 279))).isEqualTo(17);
		assertThat(BridgeRefusals.firstReason(7, List.of(279, 350))).isEqualTo(1);
		assertThat(BridgeRefusals.firstReason(8, List.of(279, 483, 2193))).isEqualTo(18);
		assertThat(BridgeRefusals.firstReason(13, List.of(350, 278, 276))).isEqualTo(14);
		assertThat(BridgeRefusals.firstReason(12, List.of(2193, 278))).isZero();
		assertThat(BridgeRefusals.firstReason(7, List.of())).isZero();
	}
}
