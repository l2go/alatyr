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
package com.l2jfree.gameserver.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;

class DatapackFilesTest
{
	@Test
	@DisplayName("a missing datapack directory is an empty list, not null")
	void missingDirectoryIsEmpty() throws Exception
	{
		File previousRoot = Config.DATAPACK_ROOT;
		File root = Files.createTempDirectory("l2jfree-datapack").toFile();
		Config.DATAPACK_ROOT = root;
		try
		{
			assertThat(Util.getDatapackFiles("mapregion", ".xml")).isNotNull().isEmpty();
		}
		finally
		{
			Config.DATAPACK_ROOT = previousRoot;
		}
	}
}
