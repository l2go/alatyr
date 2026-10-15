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

class ServerStatusTest
{
	@Test
	void protocolValuesKeepTheirOrdinals()
	{
		assertThat(ServerStatus.valueOf(4)).isEqualTo(ServerStatus.STATUS_DOWN);
		assertThat(ServerStatus.STATUS_GM_ONLY.ordinal()).isEqualTo(5);
		assertThat(ServerStatusAttributes.SERVER_AGE_LIMITATION.ordinal()).isEqualTo(9);
	}
	
	@Test
	void valuesOutOfRangeFallBack()
	{
		assertThat(ServerStatus.valueOf(-1)).isEqualTo(ServerStatus.STATUS_AUTO);
		assertThat(ServerStatus.valueOf(99)).isEqualTo(ServerStatus.STATUS_AUTO);
		assertThat(ServerStatusAttributes.valueOf(99)).isEqualTo(ServerStatusAttributes.NONE);
	}
	
	@Test
	void aWorldIsOnlineUnlessItIsDown()
	{
		assertThat(status(ServerStatus.STATUS_AUTO).online()).isTrue();
		assertThat(status(ServerStatus.STATUS_GM_ONLY).online()).isTrue();
		assertThat(status(ServerStatus.STATUS_DOWN).online()).isFalse();
	}
	
	private static WorldStatus status(ServerStatus status)
	{
		return new WorldStatus(1, status, 7777, 0, 100, 0, true, false, false, false, false, false);
	}
}
