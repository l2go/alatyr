/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.sql;

import java.util.Objects;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the numbered forward-only migrations of one PostgreSQL schema.
 * <p>
 * Each module migrates its own schema when it starts. The schema must already exist and belong to
 * the role the module connects with. A migration that was applied and later edited fails the
 * validation, so a released migration is never changed: a new version is added instead.
 */
public final class SchemaMigration
{
	private static final Logger _log = LoggerFactory.getLogger(SchemaMigration.class);
	
	private SchemaMigration()
	{
	}
	
	/**
	 * @param dataSource the pool of the role that owns the schema
	 * @param schema the schema to migrate; also the default schema of the migration run
	 * @param location the Flyway location of the migration files, for example <code>classpath:db/login</code>
	 * @return the number of migrations applied by this call
	 */
	public static int migrate(DataSource dataSource, String schema, String location)
	{
		Objects.requireNonNull(dataSource, "dataSource");
		Objects.requireNonNull(schema, "schema");
		Objects.requireNonNull(location, "location");
		
		Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.createSchemas(false)
				.locations(location)
				.validateOnMigrate(true)
				// a mispackaged or misnamed migration must stop the start, not run on an empty schema
				.failOnMissingLocations(true)
				.validateMigrationNaming(true)
				.cleanDisabled(true)
				// CREATE EXTENSION IF NOT EXISTS still emits a notice when the extension is already there.
				// That notice is not a failed migration.
				.initSql("SET client_min_messages TO warning")
				.load();
		
		MigrateResult result = flyway.migrate();
		String version = result.targetSchemaVersion;
		if (version == null && flyway.info().current() != null)
			version = String.valueOf(flyway.info().current().getVersion());
		_log.info("Schema " + schema + " is at version " + (version == null ? "none" : version) + " ("
				+ result.migrationsExecuted + " migration(s) applied).");
		return result.migrationsExecuted;
	}
}
