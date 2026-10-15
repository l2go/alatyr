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
package com.l2jfree.util.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.lang.L2Entity;

class LogoutIdentityTest
{
	@Test
	@DisplayName("logout of the old object does not drop the new object that reused its id")
	void oldRemoveLeavesTheReplacement()
	{
		L2ReadWriteEntityMap<NamedId> world = new L2ReadWriteEntityMap<NamedId>();
		NamedId loggedOut = new NamedId(7);
		NamedId loggedIn = new NamedId(7);
		
		world.add(loggedOut);
		world.add(loggedIn);
		world.remove(loggedOut);
		
		assertThat(world.get(7)).isSameAs(loggedIn);
		
		world.remove(loggedIn);
		assertThat(world.get(7)).isNull();
	}
	
	@Test
	@DisplayName("logout of the old player does not drop the new player who reused the name")
	void oldNameRemoveLeavesTheReplacement()
	{
		Map<String, String> online = new HashMap<String, String>();
		online.put("hero", "old");
		online.put("hero", "new");
		
		assertThat(SameInstance.remove(online, "hero", "old")).isFalse();
		assertThat(online.get("hero")).isEqualTo("new");
		
		assertThat(SameInstance.remove(online, "hero", "new")).isTrue();
		assertThat(online).isEmpty();
	}
	
	private static final class NamedId implements L2Entity<Integer>
	{
		private final int _id;
		
		private NamedId(int id)
		{
			_id = id;
		}
		
		@Override
		public Integer getPrimaryKey()
		{
			return _id;
		}
	}
}
