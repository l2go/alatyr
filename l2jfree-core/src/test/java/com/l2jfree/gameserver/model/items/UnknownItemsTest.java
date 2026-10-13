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
package com.l2jfree.gameserver.model.items;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnknownItemsTest
{
	@Test
	@DisplayName("items without a template are counted once for the load and are not deleted")
	void unknownTemplatesAreCountedOnce()
	{
		UnknownItems unknown = new UnknownItems();
		unknown.miss();
		unknown.miss();
		
		assertThat(unknown.count()).isEqualTo(2);
		assertThat(unknown.warning()).isEqualTo("2 items have no template in this world");
		assertThat(new UnknownItems().warning()).isNull();
	}
}
