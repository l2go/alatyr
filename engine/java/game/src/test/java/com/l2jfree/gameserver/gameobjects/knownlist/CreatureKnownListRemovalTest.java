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
package com.l2jfree.gameserver.gameobjects.knownlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;

class CreatureKnownListRemovalTest
{
	@Test
	@DisplayName("an object admitted during removal stays known")
	void objectAdmittedDuringRemovalStaysKnown() throws Exception
	{
		L2Creature owner = mock(L2Creature.class);
		when(owner.hasAI()).thenReturn(false);
		CreatureKnownList list = new CreatureKnownList(owner);
		SnapshotMap objects = new SnapshotMap();
		Field knownObjects = CreatureKnownList.class.getDeclaredField("_knownObjects");
		knownObjects.setAccessible(true);
		knownObjects.set(list, objects);
		
		L2Object present = mock(L2Object.class);
		when(present.getObjectId()).thenReturn(1);
		ObjectKnownList presentList = mock(ObjectKnownList.class);
		when(present.getKnownList()).thenReturn(presentList);
		
		L2Object admitted = mock(L2Object.class);
		when(admitted.getObjectId()).thenReturn(2);
		when(admitted.getKnownList()).thenReturn(mock(ObjectKnownList.class));
		doAnswer(invocation ->
		{
			list.getKnownObjects().put(2, admitted);
			return true;
		}).when(presentList).removeKnownObject(owner);
		
		objects.put(1, present);
		
		list.removeAllKnownObjects();
		
		assertThat(list.getKnownObjects()).doesNotContainKey(1);
		assertThat(list.knowsObject(admitted)).isTrue();
	}
	
	/**
	 * Iteration sees the objects that were already known. An add during that walk
	 * is not part of the snapshot, which is the interleaving the clear used to drop.
	 */
	private static final class SnapshotMap extends AbstractMap<Integer, L2Object>
	{
		private final Map<Integer, L2Object> store = new LinkedHashMap<Integer, L2Object>();
		
		@Override
		public Set<Entry<Integer, L2Object>> entrySet()
		{
			return store.entrySet();
		}
		
		@Override
		public L2Object put(Integer key, L2Object value)
		{
			return store.put(key, value);
		}
		
		@Override
		public L2Object remove(Object key)
		{
			return store.remove(key);
		}
		
		@Override
		public Collection<L2Object> values()
		{
			return List.copyOf(store.values());
		}
	}
}
