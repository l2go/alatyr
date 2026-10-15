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
package com.l2jfree.gameserver.gameobjects.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class SkillMapsTest
{
	@Test
	void aCreatureWithoutSkillsGetsAnEmptyMapToAddTo()
	{
		Map<Integer, String> skills = SkillMaps.modifiable(null);
		
		skills.put(1, "first");
		
		assertThat(skills).containsEntry(1, "first");
	}
	
	@Test
	void theUnmodifiableMapOfATemplateIsCopiedBeforeASkillIsAdded()
	{
		Map<Integer, String> shared = new HashMap<>();
		shared.put(1, "first");
		Map<Integer, String> template = Collections.unmodifiableMap(shared);
		
		Map<Integer, String> own = SkillMaps.modifiable(template);
		own.put(2, "second");
		
		assertThat(own).containsEntry(1, "first").containsEntry(2, "second");
		assertThat(template).containsOnlyKeys(1);
	}
	
	@Test
	void aMapThatTheCreatureAlreadyOwnsIsKept()
	{
		Map<Integer, String> own = new ConcurrentHashMap<>();
		
		assertThat(SkillMaps.modifiable(own)).isSameAs(own);
	}
}
