/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldDatabase;
import com.l2jfree.gameserver.persistence.item.ItemRepository.GroundItem;
import com.l2jfree.gameserver.persistence.item.ItemRepository.ItemAttributes;
import com.l2jfree.gameserver.persistence.item.ItemRepository.ItemRow;
import com.l2jfree.gameserver.persistence.item.ItemRepository.PaperdollEntry;

/** Items, their attributes, pets and the items on the ground on a real PostgreSQL 18. */
@Tag("integration")
class ItemRepositoryPostgresTest
{
	/** Ids that no other test uses. */
	private static final int PLAYER = 1_950_000_001;
	private static final int OTHER_PLAYER = 1_950_000_002;
	private static final int CLAN = 1_950_000_101;
	private static final int ITEM_1 = 1_950_000_201;
	private static final int ITEM_2 = 1_950_000_202;
	private static final int ITEM_3 = 1_950_000_203;
	private static final int CONTROL_ITEM = 1_950_000_204;
	private static final int PET_ITEM = 1_950_000_205;
	private static final int GROUND_1 = 1_950_000_301;
	private static final int GROUND_2 = 1_950_000_302;
	private static final String PREFIX = "ItemRepositoryPostgresTest_";

	private final ItemRepository _repository = ItemRepository.getInstance();

	@BeforeEach
	void createTheOwners()
	{
		WorldDatabase.start();
		cleanUp();
		for (int id : new int[] { PLAYER, OTHER_PLAYER })
		{
			update("INSERT INTO player (id, account_name, name, race_id, active_class_id, base_class_id) "
					+ "VALUES (?, ?, ?, 0, 0, 0)", id, PREFIX + "account", PREFIX + "player" + id);
		}
		update("INSERT INTO clan (id, name, leader_player_id) VALUES (?, ?, ?)", CLAN, PREFIX + "clan", PLAYER);
	}

	@AfterEach
	void cleanUp()
	{
		update("DELETE FROM ground_item WHERE id BETWEEN 1950000301 AND 1950000399");
		update("DELETE FROM item WHERE id BETWEEN 1950000201 AND 1950000299");
		update("DELETE FROM clan WHERE id BETWEEN 1950000101 AND 1950000199");
		update("DELETE FROM player WHERE id BETWEEN 1950000001 AND 1950000099");
	}

	private static ItemRow row(int id, String location, int slot)
	{
		return new ItemRow(id, 57, 1000, 0, location, slot, 0, 0, -1, -1);
	}

	@Test
	@DisplayName("an item is stored in the inventory of a player and read back")
	void roundTrip() throws Exception
	{
		ItemRow stored = new ItemRow(ITEM_1, 6577, 3, 7, "INVENTORY", 2, 11, 22, 120, 1_700_000_000_000L);

		_repository.insertItem(null, stored, PLAYER);

		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL")).containsExactly(stored);
		assertThat(_repository.loadItems(null, OTHER_PLAYER, "INVENTORY", "PAPERDOLL")).isEmpty();
		assertThat(string("SELECT concat_ws(',', owner_player_id, owner_clan_id, owner_pet_id) FROM item WHERE id = ?",
				ITEM_1)).isEqualTo(String.valueOf(PLAYER));
	}

	@Test
	@DisplayName("the equipped items come with the inventory, ordered by slot")
	void loadsBothLocations() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "PAPERDOLL", 10), PLAYER);
		_repository.insertItem(null, row(ITEM_2, "INVENTORY", 3), PLAYER);
		_repository.insertItem(null, row(ITEM_3, "WAREHOUSE", 0), PLAYER);

		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL")).extracting(ItemRow::id)
				.containsExactly(ITEM_2, ITEM_1);
		assertThat(_repository.loadItems(null, PLAYER, "WAREHOUSE", "WAREHOUSE")).extracting(ItemRow::id)
				.containsExactly(ITEM_3);
		assertThat(_repository.loadPaperdoll(PLAYER)).containsExactly(new PaperdollEntry(ITEM_1, 57, 10, 0));
	}

	@Test
	@DisplayName("an item is updated: count, owner, location and slot")
	void updateItem() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER);

		ItemRow moved = new ItemRow(ITEM_1, 57, 400, 3, "PAPERDOLL", 7, 1, 2, 60, 1_700_000_000_000L);
		_repository.updateItem(null, moved, OTHER_PLAYER);

		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL")).isEmpty();
		assertThat(_repository.loadItems(null, OTHER_PLAYER, "INVENTORY", "PAPERDOLL")).containsExactly(moved);
	}

	@Test
	@DisplayName("no time limit is NULL in the database and -1 in the game")
	void noTimeLimitIsNull() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER);

		assertThat(string("SELECT expire_at FROM item WHERE id = ?", ITEM_1)).isNull();
		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "INVENTORY").get(0).expireAtMillis()).isEqualTo(-1);

		// 0 also means "not set"
		_repository.updateItem(null, new ItemRow(ITEM_1, 57, 1000, 0, "INVENTORY", 0, 0, 0, -1, 0), PLAYER);
		assertThat(string("SELECT expire_at FROM item WHERE id = ?", ITEM_1)).isNull();
	}

	@Test
	@DisplayName("a clan warehouse item belongs to the clan")
	void clanWarehouse() throws Exception
	{
		ItemRow stored = row(ITEM_1, "CLANWH", 0);

		_repository.insertItem(null, stored, CLAN);

		assertThat(_repository.loadItems(null, CLAN, "CLANWH", "CLANWH")).containsExactly(stored);
		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL")).isEmpty();
		assertThat(string("SELECT concat_ws(',', owner_player_id, owner_clan_id, owner_pet_id) FROM item WHERE id = ?",
				ITEM_1)).isEqualTo(String.valueOf(CLAN));
	}

	@Test
	@DisplayName("what a pet carries belongs to the control item of the pet")
	void petItems() throws Exception
	{
		_repository.insertItem(null, row(CONTROL_ITEM, "INVENTORY", 0), PLAYER);
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) VALUES (?, 1, 1, 1, 0, 0, 0)",
				CONTROL_ITEM);
		ItemRow carried = row(PET_ITEM, "PET_EQUIP", 4);

		_repository.insertItem(null, carried, CONTROL_ITEM);

		assertThat(_repository.loadItems(null, CONTROL_ITEM, "PET", "PET_EQUIP")).containsExactly(carried);
		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "PAPERDOLL")).extracting(ItemRow::id)
				.containsExactly(CONTROL_ITEM);

		// the pet goes with its control item, and so does everything the pet carries
		_repository.deleteItem(null, CONTROL_ITEM);
		assertThat(count("SELECT count(*) FROM pet WHERE item_id = ?", CONTROL_ITEM)).isZero();
		assertThat(count("SELECT count(*) FROM item WHERE id = ?", PET_ITEM)).isZero();
	}

	@Test
	@DisplayName("deleting a pet deletes what it carries and keeps the control item")
	void deletePet() throws Exception
	{
		_repository.insertItem(null, row(CONTROL_ITEM, "INVENTORY", 0), PLAYER);
		update("INSERT INTO pet (item_id, level, current_hp, current_mp, exp, sp, current_feed) VALUES (?, 1, 1, 1, 0, 0, 0)",
				CONTROL_ITEM);
		_repository.insertItem(null, row(PET_ITEM, "PET", 0), CONTROL_ITEM);

		_repository.deletePet(CONTROL_ITEM);

		assertThat(count("SELECT count(*) FROM pet WHERE item_id = ?", CONTROL_ITEM)).isZero();
		assertThat(count("SELECT count(*) FROM item WHERE id = ?", PET_ITEM)).isZero();
		assertThat(count("SELECT count(*) FROM item WHERE id = ?", CONTROL_ITEM)).isEqualTo(1);
	}

	@Test
	@DisplayName("deleting an item deletes its attributes")
	void deleteItem() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER);
		_repository.saveAttributes(null, ITEM_1, new ItemAttributes(123, 456, 7, 2, 100));

		_repository.deleteItem(null, ITEM_1);

		assertThat(_repository.loadItems(null, PLAYER, "INVENTORY", "INVENTORY")).isEmpty();
		assertThat(_repository.loadAttributes(null, ITEM_1)).isNull();
	}

	@Test
	@DisplayName("an item that is stored twice is refused")
	void duplicateIsRefused() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER);

		assertThatThrownBy(() -> _repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER)).isInstanceOf(
				java.sql.SQLException.class);
	}

	@Test
	@DisplayName("augmentation and element are stored, updated, and NULL when the item has none")
	void attributes() throws Exception
	{
		_repository.insertItem(null, row(ITEM_1, "INVENTORY", 0), PLAYER);
		assertThat(_repository.loadAttributes(null, ITEM_1)).isNull();

		ItemAttributes both = new ItemAttributes(123, 456, 7, 2, 100);
		_repository.saveAttributes(null, ITEM_1, both);
		assertThat(_repository.loadAttributes(null, ITEM_1)).isEqualTo(both);

		// an augmentation without a skill has no skill columns, and the element can be missing
		ItemAttributes noSkill = new ItemAttributes(123, null, null, null, null);
		_repository.saveAttributes(null, ITEM_1, noSkill);
		assertThat(_repository.loadAttributes(null, ITEM_1)).isEqualTo(noSkill);
		assertThat(string("SELECT concat_ws(',', augmentation_skill_id, augmentation_skill_level, element_type, element_value) "
				+ "FROM item_attribute WHERE item_id = ?", ITEM_1)).isEmpty();

		// an element without an augmentation
		ItemAttributes elementOnly = new ItemAttributes(null, null, null, 4, 30);
		_repository.saveAttributes(null, ITEM_1, elementOnly);
		assertThat(_repository.loadAttributes(null, ITEM_1)).isEqualTo(elementOnly);

		// an item with neither has no row
		_repository.saveAttributes(null, ITEM_1, new ItemAttributes(null, null, null, null, null));
		assertThat(count("SELECT count(*) FROM item_attribute WHERE item_id = ?", ITEM_1)).isZero();
		assertThat(_repository.loadAttributes(null, ITEM_1)).isNull();
	}

	@Test
	@DisplayName("the items on the ground are rewritten as a whole")
	void groundItems() throws Exception
	{
		GroundItem dropped = new GroundItem(GROUND_1, 57, 500, 2, 10, 20, 30, 1_700_000_000_000L, false, true);
		GroundItem protectedItem = new GroundItem(GROUND_2, 6577, 1, 0, -5, 0, 7, 0, true, false);

		assertThat(_repository.replaceGroundItems(List.of(dropped, protectedItem))).isTrue();

		assertThat(_repository.loadGroundItems()).contains(dropped, protectedItem);
		// a moment that is not set is NULL
		assertThat(string("SELECT dropped_at FROM ground_item WHERE id = ?", GROUND_2)).isNull();

		// a second save replaces the first
		assertThat(_repository.replaceGroundItems(List.of(protectedItem))).isTrue();
		assertThat(_repository.loadGroundItems()).contains(protectedItem).doesNotContain(dropped);

		_repository.deleteGroundItems();
		assertThat(_repository.loadGroundItems()).isEmpty();
	}

	@Test
	@DisplayName("a failed save of the items on the ground keeps the old rows")
	void failedGroundSaveKeepsTheOldRows() throws Exception
	{
		GroundItem good = new GroundItem(GROUND_1, 57, 500, 0, 1, 2, 3, 0, false, false);
		assertThat(_repository.replaceGroundItems(List.of(good))).isTrue();

		// an empty stack breaks the check of the table
		GroundItem empty = new GroundItem(GROUND_2, 57, 0, 0, 1, 2, 3, 0, false, false);
		assertThat(_repository.replaceGroundItems(List.of(empty))).isFalse();

		assertThat(_repository.loadGroundItems()).contains(good);
	}

	@Test
	@DisplayName("protected items get a drop time when they are recycled")
	void recycleProtected() throws Exception
	{
		GroundItem plain = new GroundItem(GROUND_1, 57, 1, 0, 1, 2, 3, 0, true, false);
		GroundItem equipment = new GroundItem(GROUND_2, 6577, 1, 0, 1, 2, 3, 0, true, true);
		assertThat(_repository.replaceGroundItems(List.of(plain, equipment))).isTrue();

		_repository.recycleProtectedGroundItems(1_700_000_000_000L, false);

		assertThat(_repository.loadGroundItems()).contains(
				new GroundItem(GROUND_1, 57, 1, 0, 1, 2, 3, 1_700_000_000_000L, false, false), equipment);

		_repository.recycleProtectedGroundItems(1_700_000_001_000L, true);

		assertThat(_repository.loadGroundItems()).contains(
				new GroundItem(GROUND_2, 6577, 1, 0, 1, 2, 3, 1_700_000_001_000L, false, true));
	}

	private static void update(String sql, Object... parameters)
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
		{
			bind(statement, parameters);
			statement.executeUpdate();
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String string(String sql, Object... parameters)
	{
		try (Connection connection = L2DatabaseFactory.getInstance().getPoolConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
		{
			bind(statement, parameters);
			try (ResultSet rs = statement.executeQuery())
			{
				assertThat(rs.next()).isTrue();
				return rs.getString(1);
			}
		}
		catch (java.sql.SQLException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static long count(String sql, Object... parameters)
	{
		return Long.parseLong(string(sql, parameters));
	}

	private static void bind(PreparedStatement statement, Object[] parameters) throws java.sql.SQLException
	{
		for (int i = 0; i < parameters.length; i++)
			statement.setObject(i + 1, parameters[i]);
	}
}
