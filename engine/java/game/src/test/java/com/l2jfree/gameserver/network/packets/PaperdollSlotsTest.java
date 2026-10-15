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
package com.l2jfree.gameserver.network.packets;

import static com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The order in which packets write the paperdoll; the client reads the slots by position. */
class PaperdollSlotsTest
{
	@Test
	void withJewels()
	{
		assertThat(L2ServerPacket.getPaperdollSlots(true)).containsExactly(PAPERDOLL_UNDER, PAPERDOLL_REAR,
				PAPERDOLL_LEAR, PAPERDOLL_NECK, PAPERDOLL_RFINGER, PAPERDOLL_LFINGER, PAPERDOLL_HEAD, PAPERDOLL_RHAND,
				PAPERDOLL_LHAND, PAPERDOLL_GLOVES, PAPERDOLL_CHEST, PAPERDOLL_LEGS, PAPERDOLL_FEET, PAPERDOLL_BACK,
				PAPERDOLL_LRHAND, PAPERDOLL_HAIR, PAPERDOLL_HAIR2, PAPERDOLL_RBRACELET, PAPERDOLL_LBRACELET,
				PAPERDOLL_DECO1, PAPERDOLL_DECO2, PAPERDOLL_DECO3, PAPERDOLL_DECO4, PAPERDOLL_DECO5, PAPERDOLL_DECO6);
	}
	
	@Test
	void withoutJewels()
	{
		assertThat(L2ServerPacket.getPaperdollSlots(false)).containsExactly(PAPERDOLL_UNDER, PAPERDOLL_HEAD,
				PAPERDOLL_RHAND, PAPERDOLL_LHAND, PAPERDOLL_GLOVES, PAPERDOLL_CHEST, PAPERDOLL_LEGS, PAPERDOLL_FEET,
				PAPERDOLL_BACK, PAPERDOLL_LRHAND, PAPERDOLL_HAIR, PAPERDOLL_HAIR2, PAPERDOLL_RBRACELET,
				PAPERDOLL_LBRACELET, PAPERDOLL_DECO1, PAPERDOLL_DECO2, PAPERDOLL_DECO3, PAPERDOLL_DECO4,
				PAPERDOLL_DECO5, PAPERDOLL_DECO6);
	}
}
