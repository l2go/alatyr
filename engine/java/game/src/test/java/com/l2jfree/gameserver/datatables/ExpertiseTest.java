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
package com.l2jfree.gameserver.datatables;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;

class ExpertiseTest
{
	@Test
	@DisplayName("an empty skill tree does not grant expertise skill 239")
	void emptyTreeDoesNotGrantExpertiseSkill()
	{
		assertThat(L2Skill.SKILL_EXPERTISE).isEqualTo(239);
		assertThat(Expertise.minimumLevel(null, 1)).isEqualTo(-1);
		assertThat(Expertise.minimumLevel(Map.of(), 7)).isEqualTo(-1);
		
		int[] absent = { 0, -1, -1, -1, -1, -1, -1, -1 };
		assertThat(Expertise.skillLevel(80, absent)).isZero();
	}
	
	@Test
	@DisplayName("a defined expertise grade is granted from its minimum level")
	void definedGradeIsGranted()
	{
		L2SkillLearn learn = new L2SkillLearn(L2Skill.SKILL_EXPERTISE, 1, 20, "Expertise", 0, 0, 0);
		Map<Integer, L2SkillLearn> tree = Map.of(SkillTable.getSkillUID(L2Skill.SKILL_EXPERTISE, 1), learn);
		
		assertThat(Expertise.minimumLevel(tree, 1)).isEqualTo(20);
		assertThat(Expertise.skillLevel(20, new int[] { 0, 20, -1 })).isEqualTo(1);
	}
}
