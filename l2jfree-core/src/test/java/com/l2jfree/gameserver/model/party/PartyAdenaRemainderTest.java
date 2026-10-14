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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.util.Util;

class PartyAdenaRemainderTest
{
	@Test
	void realPartyDeliveryPreservesAndRotatesTheThirteenthCoin()
	{
		L2Player first = mock(L2Player.class);
		L2Player second = mock(L2Player.class);
		L2Attackable mob = mock(L2Attackable.class);
		L2Party party = new L2Party(first, L2Party.ITEM_LOOTER);
		party.getPartyMembers().add(second);
		try (MockedStatic<Util> range = mockStatic(Util.class))
		{
			range.when(() -> Util.checkIfInRange(anyInt(), any(), any(), eq(true))).thenReturn(true);
			party.distributeAdena(first, 13, mob);
			party.distributeAdena(first, 13, mob);
		}
		verify(first).addAdena("Party", 7, first, true);
		verify(first).addAdena("Party", 6, first, true);
		verify(second).addAdena("Party", 6, first, true);
		verify(second).addAdena("Party", 7, first, true);
	}
}
