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
package com.l2jfree.lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplaceableTest
{
	@Test
	void stringIsCopiedOnFirstReplace()
	{
		Replaceable html = Replaceable.valueOf("<a>%name%</a> %name% %count%");
		assertThat(html.indexOf("%name%", 4)).isEqualTo(14);
		
		html.replace("%name%", "Ab%name%");
		html.replace("%count%", 3);
		
		assertThat(html.toString()).isEqualTo("<a>Ab%name%</a> Ab%name% 3");
		assertThat(html.indexOf("Ab", 4)).isEqualTo(16);
		assertThat(html.substring(3, 5)).isEqualTo("Ab");
	}
	
	@Test
	void builderIsChangedInPlace()
	{
		StringBuilder sb = new StringBuilder("x %v% y");
		Replaceable.valueOf(sb).replace("%v%", "1");
		
		assertThat(sb).hasToString("x 1 y");
	}
}
