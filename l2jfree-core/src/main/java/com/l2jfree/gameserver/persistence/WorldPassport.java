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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code world.properties} from the world root and logs one line. The file is optional: without it the world is
 * unnamed. The catalog revision is known only after the catalog has been loaded.
 */
public final class WorldPassport
{
	private static final Logger _log = LoggerFactory.getLogger(WorldPassport.class);
	
	private WorldPassport()
	{
	}
	
	public static void log(Path datapackRoot, Path catalogDirectory) throws IOException
	{
		_log.info(describe(datapackRoot, catalogDirectory));
	}
	
	static String describe(Path datapackRoot, Path catalogDirectory) throws IOException
	{
		String revision = CatalogLoader.revisionOf(catalogDirectory);
		Path passport = datapackRoot.resolve("world.properties");
		if (!Files.isRegularFile(passport))
			return "World unnamed world, catalog revision " + revision;
		
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(passport))
		{
			properties.load(in);
		}
		return "World id=" + properties.getProperty("world.id", "") + " version="
				+ properties.getProperty("world.version", "") + " license=" + properties.getProperty("world.license", "")
				+ " expected scripts=" + properties.getProperty("world.expected.scripts", "") + " catalog revision "
				+ revision;
	}
}
