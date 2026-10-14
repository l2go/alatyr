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
package com.l2jfree.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;

class HuntItemOwnershipTest
{
	@Test
	void huntOwnerHasNoExpiryAndAssignmentEndsInInventory()
	{
		L2ItemInstance item = mock(L2ItemInstance.class, CALLS_REAL_METHODS);
		ScheduledFuture<?> expiry = mock(ScheduledFuture.class);
		item.setItemLootShedule(expiry);
		item.setHuntRewardOwner(17);
		verify(expiry).cancel(true);
		assertThat(item.getOwnerId()).isEqualTo(17);
		assertThat((Object)item.getItemLootShedule()).isNull();
		assertThat(item.isHuntReward()).isTrue();
		item.setLocation(L2ItemInstance.ItemLocation.INVENTORY);
		assertThat(item.isHuntReward()).isFalse();
	}
}
