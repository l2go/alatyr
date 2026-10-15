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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.entity.Castle;

class CastleManagerTest
{
	@Test
	@DisplayName("a castle without a zone is not the closest and is not asked for a distance")
	void aCastleWithoutAZoneIsNotNearby()
	{
		Castle absent = mock(Castle.class);
		when(absent.getZone()).thenReturn(null);
		
		assertThat(CastleManager.nearest(null, List.of(absent), 1, 2)).isNull();
		verify(absent, never()).getDistanceToZone(anyInt(), anyInt());
	}
	
	@Test
	@DisplayName("the castle the object is already inside is kept")
	void anInsideCastleIsKept()
	{
		Castle inside = mock(Castle.class);
		Castle absent = mock(Castle.class);
		when(absent.getZone()).thenReturn(null);
		
		assertThat(CastleManager.nearest(inside, List.of(absent), 1, 2)).isSameAs(inside);
		verify(absent, never()).getDistanceToZone(anyInt(), anyInt());
	}
}
