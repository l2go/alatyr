/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.network.packets.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldDatabase;

/** One multisell exchange is written all or nothing, on a real PostgreSQL 18. */
@Tag("integration")
class MultiSellPersistencePostgresTest
{
	/** Ids that no other test uses. */
	private static final int PLAYER = 1_952_000_001;
	private static final int CLAN = 1_952_000_101;
	private static final int INGREDIENT = 1_952_000_201;
	private static final int PRODUCT = 1_952_000_202;
	private static final int OTHER_PRODUCT = 1_952_000_203;
	/** The castle whose treasury the test changes, and the treasury it had. */
	private static final int CASTLE = 9;
	private static final String PREFIX = "MultiSellPersistencePostgresTest_";

	private long _treasury = -1;

	@BeforeEach
	void createTheRows() throws Exception
	{
		WorldDatabase.start();
		cleanUp();
		_treasury = value("SELECT treasury FROM castle WHERE id = ?", CASTLE);
		update("UPDATE castle SET treasury = 1000 WHERE id = ?", CASTLE);
		update("INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id, fame) "
				+ "VALUES (?, ?, ?, 0, 0, 0, 50)", PLAYER, PREFIX + "account", PREFIX + "player");
		update("INSERT INTO clan (id, name, leader_player_id, reputation_score) VALUES (?, ?, ?, 100)", CLAN,
				PREFIX + "clan", PLAYER);
		update("INSERT INTO item (id, item_template_id, owner_player_id, location, location_slot, count, mana_left) "
				+ "VALUES (?, 1, ?, 'INVENTORY', 0, 5, -1)", INGREDIENT, PLAYER);
	}

	@AfterEach
	void cleanUp() throws Exception
	{
		update("DELETE FROM item WHERE id BETWEEN 1952000201 AND 1952000299");
		update("DELETE FROM clan WHERE id BETWEEN 1952000101 AND 1952000199");
		update("DELETE FROM player WHERE id BETWEEN 1952000001 AND 1952000099");
		if (_treasury >= 0)
			update("UPDATE castle SET treasury = ? WHERE id = ?", _treasury, CASTLE);
	}

	@Test
	@DisplayName("a product insert that fails rolls back the earlier debit")
	void productInsertFailureRollsBackEarlierDebit() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 2);
		exchange.insert(INGREDIENT, PLAYER, 2, 1, 0);

		commitExpectingFailure(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(5);
	}

	@Test
	@DisplayName("the debit and the product are stored together")
	void successfulExchangePersistsDebitAndProductTogether() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 2);
		exchange.insert(PRODUCT, PLAYER, 2, 1, 3);

		commit(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(2);
		assertThat(count(PRODUCT)).isEqualTo(1);
		assertThat(value("SELECT enchant_level FROM item WHERE id = ?", PRODUCT)).isEqualTo(3);
		assertThat(string("SELECT location FROM item WHERE id = ?", PRODUCT)).isEqualTo("INVENTORY");
	}

	@Test
	@DisplayName("a product without a time limit has no expiry moment, a timed one has")
	void expiryMoment() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.insert(PRODUCT, PLAYER, 2, 1, 0);
		exchange.insert(OTHER_PRODUCT, PLAYER, 2, 1, 0, 90, 1_700_000_000_000L);

		commit(exchange);

		assertThat(string("SELECT expire_at FROM item WHERE id = ?", PRODUCT)).isNull();
		assertThat(value("SELECT mana_left FROM item WHERE id = ?", PRODUCT)).isEqualTo(-1);
		assertThat(value("SELECT floor(extract(epoch FROM expire_at))::bigint FROM item WHERE id = ?", OTHER_PRODUCT))
				.isEqualTo(1_700_000_000L);
		assertThat(value("SELECT mana_left FROM item WHERE id = ?", OTHER_PRODUCT)).isEqualTo(90);
	}

	@Test
	@DisplayName("a later failure restores a deleted ingredient with its attributes and pet")
	void laterFailureRestoresDeletedIngredientAndAttributes() throws Exception
	{
		update("INSERT INTO item_attribute (item_id, augmentation_attributes, element_type, element_value) "
				+ "VALUES (?, 5, 2, 100)", INGREDIENT);
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) "
				+ "VALUES (?, 1, 1, 1, 0, 0, 0)", INGREDIENT);

		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 0);
		exchange.insert(PRODUCT, PLAYER, 2, 1, 0);
		exchange.insert(PRODUCT, PLAYER, 2, 1, 0);

		commitExpectingFailure(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(5);
		assertThat(value("SELECT count(*) FROM item_attribute WHERE item_id = ?", INGREDIENT)).isEqualTo(1);
		assertThat(value("SELECT count(*) FROM pet WHERE item_id = ?", INGREDIENT)).isEqualTo(1);
	}

	@Test
	@DisplayName("a deleted ingredient takes its attributes and pet with it")
	void deletedIngredientTakesItsAttributes() throws Exception
	{
		update("INSERT INTO item_attribute (item_id, augmentation_attributes, element_type, element_value) "
				+ "VALUES (?, 5, 2, 100)", INGREDIENT);
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) "
				+ "VALUES (?, 1, 1, 1, 0, 0, 0)", INGREDIENT);

		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 0);
		commit(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(-1);
		assertThat(value("SELECT count(*) FROM item_attribute WHERE item_id = ?", INGREDIENT)).isZero();
		assertThat(value("SELECT count(*) FROM pet WHERE item_id = ?", INGREDIENT)).isZero();
	}

	@Test
	@DisplayName("an ingredient whose count changed meanwhile is refused")
	void staleIngredientIsRefused() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 4, 1);

		commitExpectingFailure(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(5);
	}

	@Test
	@DisplayName("a failed product insert rolls back the balances too")
	void failedProductInsertRollsBackAllAuxiliaryBalances() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 4);
		exchange.adjustFame(PLAYER, 50, 40);
		exchange.adjustClanReputation(CLAN, 100, 90);
		exchange.adjustCastleTreasury(CASTLE, 1000, 1010);
		exchange.insert(INGREDIENT, PLAYER, 2, 1, 0);

		commitExpectingFailure(exchange);

		assertThat(count(INGREDIENT)).isEqualTo(5);
		assertThat(value("SELECT fame FROM player WHERE id = ?", PLAYER)).isEqualTo(50);
		assertThat(value("SELECT reputation_score FROM clan WHERE id = ?", CLAN)).isEqualTo(100);
		assertThat(value("SELECT treasury FROM castle WHERE id = ?", CASTLE)).isEqualTo(1000);
	}

	@Test
	@DisplayName("the balances are written when the exchange commits")
	void balancesAreWritten() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.adjustFame(PLAYER, 50, 40);
		exchange.adjustClanReputation(CLAN, 100, 90);
		exchange.adjustCastleTreasury(CASTLE, 1000, 1010);

		commit(exchange);

		assertThat(value("SELECT fame FROM player WHERE id = ?", PLAYER)).isEqualTo(40);
		assertThat(value("SELECT reputation_score FROM clan WHERE id = ?", CLAN)).isEqualTo(90);
		assertThat(value("SELECT treasury FROM castle WHERE id = ?", CASTLE)).isEqualTo(1010);
	}

	@Test
	@DisplayName("a balance that changed meanwhile is refused")
	void staleBalanceIsRefused() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.adjustClanReputation(CLAN, 99, 90);

		commitExpectingFailure(exchange);

		assertThat(value("SELECT reputation_score FROM clan WHERE id = ?", CLAN)).isEqualTo(100);
	}

	@Test
	@DisplayName("attributes of a product commit with the item, and -1 is NULL")
	void productAttributesCommitWithItem() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.debit(INGREDIENT, PLAYER, 5, 4);
		exchange.insert(PRODUCT, PLAYER, 2, 1, 3);
		exchange.insertAttributes(PRODUCT, 123, 456, 7, (byte)2, 100);
		exchange.insert(OTHER_PRODUCT, PLAYER, 2, 1, 0);
		exchange.insertAttributes(OTHER_PRODUCT, -1, -1, -1, (byte)3, 50);

		commit(exchange);

		assertThat(count(PRODUCT)).isEqualTo(1);
		assertThat(value("SELECT augmentation_attributes FROM item_attribute WHERE item_id = ?", PRODUCT))
				.isEqualTo(123);
		assertThat(value("SELECT augmentation_skill_level FROM item_attribute WHERE item_id = ?", PRODUCT))
				.isEqualTo(7);
		assertThat(value("SELECT element_value FROM item_attribute WHERE item_id = ?", PRODUCT)).isEqualTo(100);
		// an item that has only an element has no augmentation columns
		assertThat(string("SELECT concat_ws(',', augmentation_attributes, augmentation_skill_id, augmentation_skill_level) "
				+ "FROM item_attribute WHERE item_id = ?", OTHER_PRODUCT)).isEmpty();
		assertThat(value("SELECT element_type FROM item_attribute WHERE item_id = ?", OTHER_PRODUCT)).isEqualTo(3);
	}

	@Test
	@DisplayName("the fame is written from the live balance when the row lags")
	void fameUsesTheLiveBalanceWhenTheRowLags() throws Exception
	{
		MultiSellPersistence exchange = new MultiSellPersistence();
		exchange.adjustFame(PLAYER, 70, 60);

		commit(exchange);

		assertThat(value("SELECT fame FROM player WHERE id = ?", PLAYER)).isEqualTo(60);
	}

	private static void commit(MultiSellPersistence exchange) throws SQLException
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection())
		{
			exchange.commit(connection);
		}
	}

	private static void commitExpectingFailure(MultiSellPersistence exchange) throws SQLException
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection())
		{
			assertThatThrownBy(() -> exchange.commit(connection)).isInstanceOf(SQLException.class);
		}
	}

	private static long count(int itemId) throws SQLException
	{
		String count = string("SELECT coalesce((SELECT count FROM item WHERE id = ?), -1)", itemId);
		return Long.parseLong(count);
	}

	private static void update(String sql, Object... parameters) throws SQLException
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int i = 0; i < parameters.length; i++)
				statement.setObject(i + 1, parameters[i]);
			statement.executeUpdate();
		}
	}

	private static String string(String sql, Object... parameters) throws SQLException
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int i = 0; i < parameters.length; i++)
				statement.setObject(i + 1, parameters[i]);
			try (ResultSet rs = statement.executeQuery())
			{
				assertThat(rs.next()).isTrue();
				return rs.getString(1);
			}
		}
	}

	private static long value(String sql, Object... parameters) throws SQLException
	{
		return Long.parseLong(string(sql, parameters));
	}
}
