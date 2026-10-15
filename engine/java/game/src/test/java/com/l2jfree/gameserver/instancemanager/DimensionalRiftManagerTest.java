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
package com.l2jfree.gameserver.instancemanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager.DimensionalRiftRoom;
import com.l2jfree.gameserver.model.Location;

class DimensionalRiftManagerTest
{
	@Test
	@DisplayName("a world without rift rooms is not inside a rift")
	void emptyRoomsAreNotARift() throws Exception
	{
		DimensionalRiftManager rifts = manager(new LinkedHashMap<Byte, Map<Byte, DimensionalRiftRoom>>());
		
		assertThat(rifts.checkIfInRiftZone(0, 0, 0, false)).isFalse();
		assertThat(rifts.checkIfInRiftZone(0, 0, 0, true)).isFalse();
		assertThat(rifts.checkIfInPeaceZone(0, 0, 0)).isFalse();
		assertThat(rifts.getWaitingRoomTeleport()).isNull();
		rifts.teleportToWaitingRoom(null);
	}
	
	@Test
	@DisplayName("a rift type without the rooms it names is not inside a rift")
	void aTypeWithoutItsRoomsIsNotARift() throws Exception
	{
		Map<Byte, Map<Byte, DimensionalRiftRoom>> rooms = new LinkedHashMap<Byte, Map<Byte, DimensionalRiftRoom>>();
		rooms.put((byte)0, new LinkedHashMap<Byte, DimensionalRiftRoom>());
		DimensionalRiftManager rifts = manager(rooms);
		
		assertThat(rifts.checkIfInRiftZone(0, 0, 0, false)).isFalse();
		assertThat(rifts.checkIfInPeaceZone(0, 0, 0)).isFalse();
	}
	
	@Test
	@DisplayName("loaded rooms keep the zone checks")
	void loadedRoomsKeepTheZoneChecks() throws Exception
	{
		DimensionalRiftRoom peace = mock(DimensionalRiftRoom.class);
		DimensionalRiftRoom area = mock(DimensionalRiftRoom.class);
		when(peace.checkIfInZone(1, 2, 3)).thenReturn(true);
		when(peace.checkIfInZone(4, 5, 6)).thenReturn(false);
		when(area.checkIfInZone(1, 2, 3)).thenReturn(true);
		when(area.checkIfInZone(4, 5, 6)).thenReturn(true);
		when(peace.getTeleportCoords()).thenReturn(new int[] { 7, 8, 9 });
		Map<Byte, DimensionalRiftRoom> type = new LinkedHashMap<Byte, DimensionalRiftRoom>();
		type.put((byte)0, peace);
		type.put((byte)1, area);
		Map<Byte, Map<Byte, DimensionalRiftRoom>> rooms = new LinkedHashMap<Byte, Map<Byte, DimensionalRiftRoom>>();
		rooms.put((byte)0, type);
		DimensionalRiftManager rifts = manager(rooms);
		
		assertThat(rifts.checkIfInPeaceZone(1, 2, 3)).isTrue();
		assertThat(rifts.checkIfInPeaceZone(4, 5, 6)).isFalse();
		assertThat(rifts.checkIfInRiftZone(4, 5, 6, false)).isTrue();
		assertThat(rifts.checkIfInRiftZone(1, 2, 3, true)).isFalse();
		assertThat(rifts.checkIfInRiftZone(4, 5, 6, true)).isTrue();
		Location waiting = rifts.getWaitingRoomTeleport();
		assertThat(waiting.getX()).isEqualTo(7);
		assertThat(waiting.getY()).isEqualTo(8);
		assertThat(waiting.getZ()).isEqualTo(9);
	}
	
	private static DimensionalRiftManager manager(Map<Byte, Map<Byte, DimensionalRiftRoom>> rooms) throws Exception
	{
		DimensionalRiftManager rifts = mock(DimensionalRiftManager.class, CALLS_REAL_METHODS);
		Field field = DimensionalRiftManager.class.getDeclaredField("_rooms");
		field.setAccessible(true);
		field.set(rifts, rooms);
		return rifts;
	}
}
