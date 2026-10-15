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
package com.l2jfree.gameserver.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcTypeRulesTest
{
	@Test
	void integerAccessorsFitSerialPseudoTypes()
	{
		// pgjdbc reports identity and serial columns of a result set by these names
		assertThat(JdbcTypeRules.check("getLong", "bigserial")).isNull();
		assertThat(JdbcTypeRules.check("getInt", "serial")).isNull();
		assertThat(JdbcTypeRules.check("getShort", "smallserial")).isNull();
	}
	
	@Test
	void serialPseudoTypesStayStrict()
	{
		assertThat(JdbcTypeRules.check("getString", "bigserial")).isEqualTo("getString on int8");
		assertThat(JdbcTypeRules.check("getBoolean", "serial")).isEqualTo("getBoolean on int4");
	}
}
