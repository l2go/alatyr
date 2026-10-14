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
package com.l2jfree.gameserver.model.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.gameserver.gameobjects.HuntTestPlayers;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.util.Util;

class HuntPartyLooterTest
{
	@Test
	void everyLootModeRestrictsItsChoiceToSnapshotRecipients()
	{
		L2Player unavailable = HuntTestPlayers.mockPlayer();
		L2Player available = HuntTestPlayers.mockPlayer();
		L2Attackable mob = mock(L2Attackable.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(available.getInventory()).thenReturn(inventory);
		when(inventory.validateCapacityByItemId(60001)).thenReturn(true);
		try (MockedStatic<Util> range = mockStatic(Util.class))
		{
			range.when(() -> Util.checkIfInRange(anyInt(), any(), any(), eq(true))).thenReturn(true);
			for (int mode : List.of(L2Party.ITEM_LOOTER, L2Party.ITEM_RANDOM, L2Party.ITEM_ORDER,
					L2Party.ITEM_RANDOM_SPOIL, L2Party.ITEM_ORDER_SPOIL))
			{
				L2Party party = new L2Party(unavailable, mode);
				party.getPartyMembers().add(available);
				assertThat(party.getHuntLooter(unavailable, 60001, mob, List.of(available))).isSameAs(available);
			}
		}
		verify(unavailable, never()).getInventory();
	}
}
