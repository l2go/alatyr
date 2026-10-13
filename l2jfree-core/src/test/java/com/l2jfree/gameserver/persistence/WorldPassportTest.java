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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldPassportTest
{
	@Test
	@DisplayName("a world passport names the id and the catalog revision")
	void passportNamesTheWorld(@TempDir Path root) throws Exception
	{
		Path catalog = root.resolve("catalog");
		Files.createDirectory(catalog);
		Files.writeString(root.resolve("world.properties"), "world.id=empty\nworld.version=0\n");
		
		assertThat(WorldPassport.describe(root, catalog)).contains("id=empty").contains("version=0")
				.contains("catalog revision");
	}
	
	@Test
	@DisplayName("a missing catalog directory names the key and the path")
	void missingCatalogNamesTheKey(@TempDir Path root)
	{
		Path missing = root.resolve("missing");
		assertThatThrownBy(() -> CatalogLoader.revisionOf(missing)).isInstanceOf(WorldConfigurationException.class)
				.hasMessageContaining("CatalogDirectory").hasMessageContaining(missing.toString());
	}
}
