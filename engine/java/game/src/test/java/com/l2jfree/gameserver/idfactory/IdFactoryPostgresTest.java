/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.idfactory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldDatabase;

/** The ids the id factory reserves at start, read from the real schema on PostgreSQL 18. */
@Tag("integration")
class IdFactoryPostgresTest
{
	/** Ids that no other test uses. */
	private static final int PLAYER_1 = 1_951_000_001;
	private static final int PLAYER_2 = 1_951_000_002;
	private static final int CLAN = 1_951_000_101;
	private static final int CLAN_CREST = 1_951_000_102;
	private static final int LARGE_CREST = 1_951_000_103;
	private static final int ALLIANCE_CREST = 1_951_000_104;
	private static final int COUPLE = 1_951_000_201;
	private static final int ITEM = 1_951_000_301;
	private static final int CONTROL_ITEM = 1_951_000_302;
	private static final int GROUND_ITEM = 1_951_000_303;
	private static final int STORED_CREST = 1_951_000_401;
	private static final String PREFIX = "IdFactoryPostgresTest_";

	@BeforeEach
	void start()
	{
		WorldDatabase.start();
		cleanUp();
	}

	@AfterEach
	void cleanUp()
	{
		update("DELETE FROM ground_item WHERE id BETWEEN 1951000301 AND 1951000399");
		update("DELETE FROM item WHERE id BETWEEN 1951000301 AND 1951000399");
		update("DELETE FROM couple WHERE id BETWEEN 1951000201 AND 1951000299");
		update("DELETE FROM clan WHERE id BETWEEN 1951000101 AND 1951000199");
		update("DELETE FROM crest WHERE id BETWEEN 1951000401 AND 1951000499");
		update("DELETE FROM player WHERE id BETWEEN 1951000001 AND 1951000099");
	}

	@Test
	@DisplayName("every persisted id is reserved on restart")
	void allPersistedIdsAreReserved() throws Exception
	{
		insertPlayers();
		update("INSERT INTO clan (id, name, leader_player_id, crest_id, large_crest_id, alliance_crest_id) VALUES (?, ?, ?, ?, ?, ?)",
				CLAN, PREFIX + "clan", PLAYER_1, CLAN_CREST, LARGE_CREST, ALLIANCE_CREST);
		update("INSERT INTO couple (id, player1_id, player2_id, engaged_at) VALUES (?, ?, ?, now())", COUPLE, PLAYER_1,
				PLAYER_2);
		insertItem(ITEM);
		insertItem(CONTROL_ITEM);
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) VALUES (?, 1, 1, 1, 0, 0, 0)",
				CONTROL_ITEM);
		update("INSERT INTO ground_item (id, item_template_id, count, x, y, z) VALUES (?, 57, 1, 0, 0, 0)", GROUND_ITEM);
		update("INSERT INTO crest (id, kind, image) VALUES (?, 'clan', decode('00', 'hex'))", STORED_CREST);

		int[] ids = read();

		assertThat(ids).contains(PLAYER_1, PLAYER_2, CLAN, CLAN_CREST, LARGE_CREST, ALLIANCE_CREST, COUPLE, ITEM,
				CONTROL_ITEM, GROUND_ITEM, STORED_CREST);
		assertThat(ids).isSorted();
	}

	@Test
	@DisplayName("an id that is stored in two places is listed once")
	void anIdIsListedOnce() throws Exception
	{
		insertPlayers();
		insertItem(CONTROL_ITEM);
		// the pet has the id of its control item
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) VALUES (?, 1, 1, 1, 0, 0, 0)",
				CONTROL_ITEM);
		// the member clans of an alliance share a crest
		update("INSERT INTO clan (id, name, leader_player_id, alliance_crest_id) VALUES (?, ?, ?, ?)", CLAN,
				PREFIX + "clan", PLAYER_1, ALLIANCE_CREST);
		update("INSERT INTO crest (id, kind, image) VALUES (?, 'alliance', decode('00', 'hex'))", ALLIANCE_CREST);

		int[] ids = read();

		assertThat(Arrays.stream(ids).filter(id -> id == CONTROL_ITEM).count()).isEqualTo(1);
		assertThat(Arrays.stream(ids).filter(id -> id == ALLIANCE_CREST).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("a deleted object, a removed crest and an unset crest are no longer reserved")
	void releasedIdsAreNotReserved() throws Exception
	{
		insertPlayers();
		update("INSERT INTO clan (id, name, leader_player_id, crest_id) VALUES (?, ?, ?, ?)", CLAN, PREFIX + "clan",
				PLAYER_1, CLAN_CREST);
		update("INSERT INTO couple (id, player1_id, player2_id, engaged_at) VALUES (?, ?, ?, now())", COUPLE, PLAYER_1,
				PLAYER_2);
		insertItem(ITEM);
		assertThat(read()).contains(CLAN_CREST, COUPLE, ITEM);

		// NULL is "no crest"
		update("UPDATE clan SET crest_id = NULL WHERE id = ?", CLAN);
		update("DELETE FROM couple WHERE id = ?", COUPLE);
		update("DELETE FROM item WHERE id = ?", ITEM);

		int[] ids = read();
		assertThat(ids).contains(CLAN).doesNotContain(CLAN_CREST, COUPLE, ITEM);
	}

	@Test
	@DisplayName("the check statements run against the schema")
	void checkStatementsRun() throws Exception
	{
		int unused = 1_951_999_990;
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection())
		{
			for (String sql : IdFactory.ID_CHECKS)
			{
				try (PreparedStatement statement = connection.prepareStatement(sql))
				{
					statement.setInt(1, unused);
					statement.setInt(2, unused + 1);
					assertThat(statement.executeQuery().next()).as(sql).isFalse();
				}
			}
		}
	}

	private static int[] read() throws Exception
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection())
		{
			return PersistedObjectIds.read(connection);
		}
	}

	private static void insertPlayers()
	{
		for (int id : new int[] { PLAYER_1, PLAYER_2 })
		{
			update("INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id) "
					+ "VALUES (?, ?, ?, 0, 0, 0)", id, PREFIX + "account", PREFIX + "player" + id);
		}
	}

	private static void insertItem(int id)
	{
		update("INSERT INTO item (id, item_template_id, owner_player_id, location, count) VALUES (?, 57, ?, 'INVENTORY', 1)",
				id, PLAYER_1);
	}

	private static void update(String sql, Object... parameters)
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int i = 0; i < parameters.length; i++)
				statement.setObject(i + 1, parameters[i]);
			statement.executeUpdate();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
}
