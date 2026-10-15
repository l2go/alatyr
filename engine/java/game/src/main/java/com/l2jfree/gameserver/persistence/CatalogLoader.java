/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the <code>catalog</code> schema equal to the catalog shipped with the server.
 * <p>
 * The catalog is one CSV file per table. The revision of the catalog is the SHA-256 of those files. At start the loader
 * compares it with the revision stored in the database. When they differ, it empties the schema, creates the tables
 * again, and loads every file with <code>COPY</code>, all in one transaction. A player never sees a half-loaded catalog,
 * and the world data is not touched. When the revisions match, the loader does nothing.
 */
public final class CatalogLoader
{
	private static final Logger _log = LoggerFactory.getLogger(CatalogLoader.class);
	
	/** The table definitions of the catalog, applied in this order with the catalog schema first in the search path. */
	private static final List<String> DEFINITIONS = List.of("/db/catalog/01_item_skill.sql", "/db/catalog/02_npc_spawn.sql");
	
	private static final String REVISION_TABLE = "catalog_revision";
	
	private CatalogLoader()
	{
	}
	
	/**
	 * @return true if the catalog was loaded, false if the database already held this revision
	 */
	public static boolean ensureCurrent(Connection connection, Path directory) throws SQLException, IOException
	{
		String revision = revisionOf(directory);
		String loaded = loadedRevision(connection);
		if (revision.equals(loaded))
		{
			_log.info("Catalog revision " + revision + " is current.");
			return false;
		}
		
		_log.info("Loading catalog revision " + revision + " (the database holds " + (loaded == null ? "none" : loaded) + ").");
		load(connection, directory, revision);
		return true;
	}
	
	/**
	 * The SHA-256 over the names and the contents of the CSV files of the directory, in name order, and over the
	 * definitions of the tables, so a change of either one loads the catalog again.
	 */
	public static String revisionOf(Path directory) throws IOException
	{
		MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is not available", e);
		}
		
		for (Path file : csvFiles(directory))
		{
			digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
			digest.update((byte)0);
			digest.update(Files.readAllBytes(file));
			digest.update((byte)0);
		}
		for (String definition : DEFINITIONS)
		{
			digest.update(resource(definition).getBytes(StandardCharsets.UTF_8));
			digest.update((byte)0);
		}
		return HexFormat.of().formatHex(digest.digest());
	}
	
	/** The revision the database holds, or null when there is no catalog. */
	public static String loadedRevision(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT to_regclass('catalog." + REVISION_TABLE + "') IS NOT NULL"))
		{
			result.next();
			if (!result.getBoolean(1))
			{
				return null;
			}
		}
		
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT revision FROM catalog." + REVISION_TABLE))
		{
			return result.next() ? result.getString(1) : null;
		}
	}
	
	/** Replaces the catalog with the files of the directory, in one transaction. */
	static void load(Connection connection, Path directory, String revision) throws SQLException, IOException
	{
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try
		{
			emptyCatalog(connection);
			createTables(connection);
			long rows = copyFiles(connection, directory);
			moveIdentitiesPastTheLoadedIds(connection);
			
			try (PreparedStatement statement = connection
					.prepareStatement("INSERT INTO catalog." + REVISION_TABLE + " (revision, loaded_at) VALUES (?, now())"))
			{
				statement.setString(1, revision);
				statement.executeUpdate();
			}
			
			// The views read the catalog, so they are created again after it changed.
			ReportViews.recreate(connection);
			connection.commit();
			_log.info("Catalog revision " + revision + " loaded: " + rows + " rows.");
		}
		catch (SQLException | IOException | RuntimeException | Error e)
		{
			connection.rollback();
			throw e;
		}
		finally
		{
			connection.setAutoCommit(autoCommit);
		}
	}
	
	private static List<Path> csvFiles(Path directory) throws IOException
	{
		if (!Files.isDirectory(directory))
			throw new WorldConfigurationException("CatalogDirectory", directory.toString());
		
		try (Stream<Path> files = Files.list(directory))
		{
			return files.filter(file -> file.getFileName().toString().endsWith(".csv")).sorted().toList();
		}
	}
	
	/** The schema is owned by the role of the server, so its content is dropped and the schema stays. */
	private static void emptyCatalog(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("DO $$ DECLARE t record; BEGIN "
					+ "FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'catalog' LOOP "
					+ "EXECUTE format('DROP TABLE catalog.%I CASCADE', t.tablename); END LOOP; END $$");
		}
	}
	
	private static void createTables(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("SET LOCAL search_path = catalog, world, public");
			for (String definition : DEFINITIONS)
			{
				statement.execute(resource(definition));
			}
			statement.execute("CREATE UNLOGGED TABLE " + REVISION_TABLE + " (revision text PRIMARY KEY, "
					+ "loaded_at timestamptz NOT NULL)");
			statement.execute("COMMENT ON TABLE " + REVISION_TABLE + " IS 'The revision of the loaded catalog. Unlogged "
					+ "like the catalog, so a crash clears both together.'");
			statement.execute("COMMENT ON COLUMN " + REVISION_TABLE + ".revision IS 'SHA-256 of the catalog files.'");
			statement.execute("COMMENT ON COLUMN " + REVISION_TABLE + ".loaded_at IS 'The moment the catalog was loaded.'");
		}
	}
	
	/** Loads each file into the table of the same name. The column list is the header of the file. */
	private static long copyFiles(Connection connection, Path directory) throws SQLException, IOException
	{
		var copy = connection.unwrap(PGConnection.class).getCopyAPI();
		long rows = 0;
		List<String> order = loadOrder(connection);
		
		// a file without a table is a typo or a stale file: loading without it would lose its rows silently
		for (Path file : csvFiles(directory))
		{
			String name = file.getFileName().toString();
			if (!order.contains(name.substring(0, name.length() - ".csv".length())))
				throw new IllegalStateException("The catalog file " + name + " has no table in the catalog schema");
		}
		
		for (String table : order)
		{
			Path file = directory.resolve(table + ".csv");
			if (!Files.exists(file))
			{
				continue; // a table without rows ships no file
			}
			
			String header;
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				header = reader.readLine();
			}
			if (header == null)
			{
				continue;
			}
			
			try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				rows += copy.copyIn("COPY catalog." + table + " (" + header + ") FROM STDIN (FORMAT csv, HEADER MATCH)", reader);
			}
		}
		return rows;
	}
	
	/** The catalog tables, each after the tables it references. */
	static List<String> loadOrder(Connection connection) throws SQLException
	{
		Map<String, Set<String>> references = new LinkedHashMap<String, Set<String>>();
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
						"SELECT tablename FROM pg_tables WHERE schemaname = 'catalog' ORDER BY tablename"))
		{
			while (result.next())
			{
				references.put(result.getString(1), new LinkedHashSet<String>());
			}
		}
		
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT child.relname, parent.relname "
						+ "FROM pg_constraint k "
						+ "JOIN pg_class child ON child.oid = k.conrelid "
						+ "JOIN pg_class parent ON parent.oid = k.confrelid "
						+ "JOIN pg_namespace child_schema ON child_schema.oid = child.relnamespace "
						+ "JOIN pg_namespace parent_schema ON parent_schema.oid = parent.relnamespace "
						+ "WHERE k.contype = 'f' AND child_schema.nspname = 'catalog' AND parent_schema.nspname = 'catalog'"))
		{
			while (result.next())
			{
				String child = result.getString(1);
				String parent = result.getString(2);
				if (!child.equals(parent))
				{
					references.get(child).add(parent);
				}
			}
		}
		
		List<String> order = new ArrayList<String>();
		Set<String> done = new LinkedHashSet<String>();
		for (String table : references.keySet())
		{
			visit(table, references, done, new LinkedHashSet<String>(), order);
		}
		order.remove(REVISION_TABLE);
		return order;
	}
	
	private static void visit(String table, Map<String, Set<String>> references, Set<String> done, Set<String> path,
			List<String> order)
	{
		if (done.contains(table))
		{
			return;
		}
		if (!path.add(table))
		{
			throw new IllegalStateException("The catalog has a foreign key cycle through " + path);
		}
		for (String parent : references.get(table))
		{
			visit(parent, references, done, path, order);
		}
		path.remove(table);
		done.add(table);
		order.add(table);
	}
	
	/** Rows were loaded with their ids, so the identity sequences move past them and an insert does not collide. */
	private static void moveIdentitiesPastTheLoadedIds(Connection connection) throws SQLException
	{
		List<String[]> identities = new ArrayList<String[]>();
		try (Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT c.relname, a.attname FROM pg_attribute a "
						+ "JOIN pg_class c ON c.oid = a.attrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
						+ "WHERE n.nspname = 'catalog' AND a.attidentity <> ''"))
		{
			while (result.next())
			{
				identities.add(new String[] { result.getString(1), result.getString(2) });
			}
		}
		
		for (String[] identity : identities)
		{
			String table = "catalog." + identity[0];
			try (Statement statement = connection.createStatement())
			{
				statement.execute("SELECT setval(pg_get_serial_sequence('" + table + "', '" + identity[1]
						+ "'), COALESCE((SELECT max(" + identity[1] + ") FROM " + table + "), 0) + 1, false)");
			}
		}
	}
	
	private static String resource(String name)
	{
		try (InputStream input = CatalogLoader.class.getResourceAsStream(name))
		{
			if (input == null)
			{
				throw new IllegalStateException("Missing resource " + name);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
