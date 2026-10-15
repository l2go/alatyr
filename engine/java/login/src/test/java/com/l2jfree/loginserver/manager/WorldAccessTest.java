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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.WorldStatus;
import com.l2jfree.loginserver.services.exception.MaintenanceException;
import com.l2jfree.loginserver.services.exception.MaturityException;

class WorldAccessTest
{
	private static final int GM_MIN = 100;
	private static final int PLAYER = 0;
	private static final int GM = 100;
	
	private static WorldStatus world(ServerStatus status, int ageLimit)
	{
		return new WorldStatus(1, status, 7777, 0, 100, ageLimit, false, false, false, false, false, false);
	}
	
	@Test
	@DisplayName("a world that is down is under maintenance for everyone, game masters included")
	void downWorldIsUnderMaintenance()
	{
		WorldStatus down = world(ServerStatus.STATUS_DOWN, 0);
		
		assertThatThrownBy(() -> WorldAccess.check(down, 30, PLAYER, GM_MIN)).isSameAs(MaintenanceException.MAINTENANCE);
		assertThatThrownBy(() -> WorldAccess.check(down, 30, GM, GM_MIN)).isSameAs(MaintenanceException.MAINTENANCE);
	}
	
	@Test
	@DisplayName("a GM-only world refuses a player and admits a game master")
	void gmOnlyWorld()
	{
		WorldStatus gmOnly = world(ServerStatus.STATUS_GM_ONLY, 0);
		
		assertThatThrownBy(() -> WorldAccess.check(gmOnly, 30, GM_MIN - 1, GM_MIN))
				.isSameAs(MaintenanceException.MAINTENANCE);
		assertThatCode(() -> WorldAccess.check(gmOnly, 30, GM_MIN, GM_MIN)).doesNotThrowAnyException();
	}
	
	@Test
	@DisplayName("an account younger than the age limit is refused and one at the limit is admitted")
	void ageLimit()
	{
		WorldStatus adults = world(ServerStatus.STATUS_AUTO, 18);
		
		assertThatThrownBy(() -> WorldAccess.check(adults, 17, PLAYER, GM_MIN)).isInstanceOf(MaturityException.class);
		assertThatCode(() -> WorldAccess.check(adults, 18, PLAYER, GM_MIN)).doesNotThrowAnyException();
		assertThatCode(() -> WorldAccess.check(adults, 40, PLAYER, GM_MIN)).doesNotThrowAnyException();
	}
	
	@Test
	@DisplayName("an open world with no age limit admits everyone")
	void openWorld()
	{
		WorldStatus open = world(ServerStatus.STATUS_AUTO, 0);
		
		assertThatCode(() -> WorldAccess.check(open, 0, PLAYER, GM_MIN)).doesNotThrowAnyException();
	}
	
	@Test
	@DisplayName("a full world refuses a player and admits a game master")
	void fullWorld()
	{
		WorldStatus full = new WorldStatus(1, ServerStatus.STATUS_AUTO, 7777, 100, 100, 0, false, false, false, false,
				false, false);
		
		assertThat(WorldAccess.hasPlaceFor(full, PLAYER, GM_MIN)).isFalse();
		assertThat(WorldAccess.hasPlaceFor(full, GM, GM_MIN)).isTrue();
	}
	
	@Test
	@DisplayName("a world with a free place admits a player")
	void freePlace()
	{
		WorldStatus free = new WorldStatus(1, ServerStatus.STATUS_AUTO, 7777, 99, 100, 0, false, false, false, false,
				false, false);
		
		assertThat(WorldAccess.hasPlaceFor(free, PLAYER, GM_MIN)).isTrue();
	}
}
