/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.l2jfree.sql.SchemaMigration;

/**
 * Brings the schemas of the world module up to date before the server accepts a player.
 * <ol>
 * <li>The report views are dropped, because a view keeps a migration from changing a column it reads.</li>
 * <li>The migrations of the <code>world</code> schema are applied.</li>
 * <li>The <code>catalog</code> schema is made equal to the catalog files of the image.</li>
 * <li>The report views are created again.</li>
 * </ol>
 * The schemas themselves and the role that owns them are created once by the database initialization;
 * this class never needs the right to create a schema.
 */
public final class WorldSchemas
{
	/** Schema of the game state: the backup. */
	public static final String WORLD = "world";
	
	/** Schema of the game content: rebuilt from the image, never backed up. */
	public static final String CATALOG = "catalog";
	
	/** Schema of the read-only views for people. */
	public static final String REPORT = "report";
	
	private WorldSchemas()
	{
	}
	
	public static void prepare(DataSource source, Path catalogDirectory) throws SQLException, IOException
	{
		try (Connection connection = source.getConnection())
		{
			ReportViews.drop(connection);
		}
		
		SchemaMigration.migrate(source, WORLD, "classpath:db/world");
		
		try (Connection connection = source.getConnection())
		{
			boolean loaded = CatalogLoader.ensureCurrent(connection, catalogDirectory);
			if (!loaded)
			{
				// A catalog load creates the views itself; otherwise this server version may define new ones.
				ReportViews.recreate(connection);
			}
		}
	}
}
