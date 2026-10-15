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
package com.l2jfree.gameserver.instancemanager.grandbosses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;

class BossInitTest
{
	@Test
	@DisplayName("a missing boss template is one info line and does not throw")
	void missingTemplateIsOneInfoLine()
	{
		Logger log = mock(Logger.class);
		
		assertThat(BossInit.missing(log, "Antharas", null)).isTrue();
		
		verify(log).info("World has no Antharas template");
		verifyNoMoreInteractions(log);
	}
	
	@Test
	@DisplayName("a boss that has a template is not reported as missing")
	void presentTemplateIsKept()
	{
		Logger log = mock(Logger.class);
		
		assertThat(BossInit.missing(log, "Antharas", mock(L2NpcTemplate.class))).isFalse();
		
		verifyNoMoreInteractions(log);
	}
}
