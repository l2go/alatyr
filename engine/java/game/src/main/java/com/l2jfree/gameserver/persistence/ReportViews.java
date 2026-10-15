/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The read-only views of the <code>report</code> schema, one per question an operator asks.
 * <p>
 * The views read the catalog, which is replaced wholesale, so the server creates them again on every start and after
 * every catalog load. Nothing writes to the schema. A role named {@value #READER_ROLE} may read the views and the
 * catalog when the operator has created it.
 */
public final class ReportViews
{
	/** The role operators and tools use to read the reports. The operator creates it; the server never does. */
	public static final String READER_ROLE = "l2_readonly";
	
	private static final String DEFINITIONS = "/db/report/views.sql";
	
	private ReportViews()
	{
	}
	
	/** Drops every view of the report schema. The schema stays, because it is owned by the role of the server. */
	public static void drop(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute("DO $$ DECLARE v record; BEGIN "
					+ "FOR v IN SELECT viewname FROM pg_views WHERE schemaname = 'report' LOOP "
					+ "EXECUTE format('DROP VIEW IF EXISTS report.%I CASCADE', v.viewname); END LOOP; END $$");
		}
	}
	
	/** Creates the views again and grants them to the reader role if it exists. Runs in the caller's transaction. */
	public static void recreate(Connection connection) throws SQLException
	{
		drop(connection);
		try (Statement statement = connection.createStatement())
		{
			statement.execute(definitions());
			if (readerExists(statement))
			{
				statement.execute("GRANT USAGE ON SCHEMA report, catalog TO " + READER_ROLE);
				statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA report, catalog TO " + READER_ROLE);
			}
		}
	}
	
	private static boolean readerExists(Statement statement) throws SQLException
	{
		try (ResultSet result = statement.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = '" + READER_ROLE + "'"))
		{
			return result.next();
		}
	}
	
	private static String definitions()
	{
		try (InputStream input = ReportViews.class.getResourceAsStream(DEFINITIONS))
		{
			if (input == null)
			{
				throw new IllegalStateException("Missing resource " + DEFINITIONS);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
