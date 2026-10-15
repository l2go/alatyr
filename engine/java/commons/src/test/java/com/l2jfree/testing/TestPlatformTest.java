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
package com.l2jfree.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Proves the test platform: JUnit 6, AssertJ, and the Mockito agent on JDK 25.
 * Product regressions replace this as defects are fixed. It is not a characterization of server behavior.
 */
@ExtendWith(MockitoExtension.class)
class TestPlatformTest
{
	@Mock
	private List<String> values;
	
	@Test
	@DisplayName("JUnit 6, AssertJ, and the Mockito agent run on this JDK")
	void runnerIsOnTheClasspath()
	{
		when(values.size()).thenReturn(1);
		
		assertThat(values).hasSize(1);
	}
}
