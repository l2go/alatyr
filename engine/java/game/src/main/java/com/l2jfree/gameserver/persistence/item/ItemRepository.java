/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.l2jfree.gameserver.persistence.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * The SQL of the item instances (<code>world.item</code>), what hangs off them (<code>item_attribute</code>,
 * <code>pet</code>), and the items on the ground (<code>world.ground_item</code>).
 * <p>
 * The model classes keep the game logic and call this class with plain values. Every operation takes its connection from
 * {@link L2DatabaseFactory#getConnection(Connection)}, so it joins a surrounding
 * {@link com.l2jfree.gameserver.persistence.WorldTransaction}. A method that takes a connection uses that connection and
 * leaves it open; a method that gets none opens one and closes it.
 * <p>
 * The location of an item is the name of the Java enum <code>L2ItemInstance.ItemLocation</code>. It decides which owner
 * column holds the owner key: CLANWH uses <code>owner_clan_id</code>, PET and PET_EQUIP use <code>owner_pet_id</code>
 * (the control item of the pet), every other location uses <code>owner_player_id</code>.
 */
public final class ItemRepository
{
	private static final String CLAN_WAREHOUSE = "CLANWH";
	private static final String PET = "PET";
	private static final String PET_EQUIPPED = "PET_EQUIP";

	private static final String ITEM_COLUMNS =
			"id, item_template_id, count, enchant_level, location, location_slot, custom_type1, custom_type2, mana_left, expire_at";

	private static final String SELECT_PLAYER_ITEMS = "SELECT " + ITEM_COLUMNS
			+ " FROM item WHERE owner_player_id = ? AND location IN (?, ?) ORDER BY location_slot, id";
	private static final String SELECT_CLAN_ITEMS = "SELECT " + ITEM_COLUMNS
			+ " FROM item WHERE owner_clan_id = ? AND location IN (?, ?) ORDER BY location_slot, id";
	private static final String SELECT_PET_ITEMS = "SELECT " + ITEM_COLUMNS
			+ " FROM item WHERE owner_pet_id = ? AND location IN (?, ?) ORDER BY location_slot, id";

	private static final String SELECT_PAPERDOLL =
			"SELECT id, item_template_id, location_slot, enchant_level FROM item WHERE owner_player_id = ? AND location = 'PAPERDOLL'";

	private static final String INSERT_ITEM =
			"INSERT INTO item (id, item_template_id, owner_player_id, owner_clan_id, owner_pet_id, location, location_slot, count,"
					+ " enchant_level, custom_type1, custom_type2, mana_left, expire_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_ITEM =
			"UPDATE item SET owner_player_id = ?, owner_clan_id = ?, owner_pet_id = ?, count = ?, location = ?, location_slot = ?,"
					+ " enchant_level = ?, custom_type1 = ?, custom_type2 = ?, mana_left = ?, expire_at = ? WHERE id = ?";
	private static final String DELETE_ITEM = "DELETE FROM item WHERE id = ?";

	private static final String SELECT_ATTRIBUTES =
			"SELECT augmentation_attributes, augmentation_skill_id, augmentation_skill_level, element_type, element_value"
					+ " FROM item_attribute WHERE item_id = ?";
	private static final String UPSERT_ATTRIBUTES =
			"INSERT INTO item_attribute (item_id, augmentation_attributes, augmentation_skill_id, augmentation_skill_level,"
					+ " element_type, element_value) VALUES (?, ?, ?, ?, ?, ?)"
					+ " ON CONFLICT (item_id) DO UPDATE SET augmentation_attributes = EXCLUDED.augmentation_attributes,"
					+ " augmentation_skill_id = EXCLUDED.augmentation_skill_id,"
					+ " augmentation_skill_level = EXCLUDED.augmentation_skill_level,"
					+ " element_type = EXCLUDED.element_type, element_value = EXCLUDED.element_value";
	private static final String DELETE_ATTRIBUTES = "DELETE FROM item_attribute WHERE item_id = ?";

	private static final String DELETE_PET = "DELETE FROM pet WHERE item_id = ?";

	private static final String SELECT_GROUND_ITEMS =
			"SELECT id, item_template_id, count, enchant_level, x, y, z, dropped_at, is_protected, is_equipable FROM ground_item";
	private static final String INSERT_GROUND_ITEM =
			"INSERT INTO ground_item (id, item_template_id, count, enchant_level, x, y, z, dropped_at, is_protected, is_equipable)"
					+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
	private static final String DELETE_GROUND_ITEMS = "DELETE FROM ground_item";
	private static final String UNPROTECT_GROUND_ITEMS =
			"UPDATE ground_item SET dropped_at = ?, is_protected = false WHERE is_protected";
	private static final String UNPROTECT_GROUND_ITEMS_EXCEPT_EQUIPABLE =
			"UPDATE ground_item SET dropped_at = ?, is_protected = false WHERE is_protected AND NOT is_equipable";

	/**
	 * An item instance as the table stores it.
	 *
	 * @param id object id of the item
	 * @param itemTemplateId item template (catalog)
	 * @param count pieces in the stack
	 * @param enchantLevel enchant level
	 * @param location name of the location (<code>L2ItemInstance.ItemLocation</code>)
	 * @param locationSlot position inside the location
	 * @param customType1 first custom value
	 * @param customType2 second custom value
	 * @param manaLeft remaining mana of a shadow item, -1 for any other item
	 * @param expireAtMillis epoch milliseconds when the item disappears, -1 for an item without a time limit
	 */
	public record ItemRow(int id, int itemTemplateId, long count, int enchantLevel, String location, int locationSlot,
			int customType1, int customType2, int manaLeft, long expireAtMillis)
	{
	}

	/** The augmentation and the elemental attribute of an item. A value is null when the item does not have it. */
	public record ItemAttributes(Integer augmentationAttributes, Integer augmentationSkillId,
			Integer augmentationSkillLevel, Integer elementType, Integer elementValue)
	{
		/** True when the item has neither an augmentation nor an elemental attribute: no row is stored then. */
		public boolean isEmpty()
		{
			return augmentationAttributes == null && elementType == null;
		}
	}

	/** One worn item of a player, as the character selection shows it. */
	public record PaperdollEntry(int id, int itemTemplateId, int slot, int enchantLevel)
	{
	}

	/**
	 * An item that lies on the ground.
	 *
	 * @param droppedAtMillis epoch milliseconds the item was dropped, 0 when it has no drop time
	 * @param isProtected true when the item is protected from auto-destroy
	 */
	public record GroundItem(int id, int itemTemplateId, long count, int enchantLevel, int x, int y, int z,
			long droppedAtMillis, boolean isProtected, boolean isEquipable)
	{
	}

	private static final class SingletonHolder
	{
		private static final ItemRepository INSTANCE = new ItemRepository();
	}

	public static ItemRepository getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private ItemRepository()
	{
	}

	// Items -----------------------------------------------------------------------------------------------------------

	/**
	 * @param con a connection to use, or null
	 * @param ownerKey id of the owner: a player, a clan, or the control item of a pet, as the base location says
	 * @param baseLocation location of the container
	 * @param equipLocation second location to load (the equipped items), may be the same as the base location
	 * @return the items of the owner in the two locations, ordered by location slot
	 */
	public List<ItemRow> loadItems(Connection con, int ownerKey, String baseLocation, String equipLocation)
			throws SQLException
	{
		String sql;
		if (CLAN_WAREHOUSE.equals(baseLocation))
			sql = SELECT_CLAN_ITEMS;
		else if (isPetLocation(baseLocation))
			sql = SELECT_PET_ITEMS;
		else
			sql = SELECT_PLAYER_ITEMS;

		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try (PreparedStatement statement = used.prepareStatement(sql))
		{
			statement.setInt(1, ownerKey);
			statement.setString(2, baseLocation);
			statement.setString(3, equipLocation);
			try (ResultSet rs = statement.executeQuery())
			{
				List<ItemRow> items = new ArrayList<ItemRow>();
				while (rs.next())
				{
					Timestamp expireAt = rs.getTimestamp("expire_at");
					items.add(new ItemRow(rs.getInt("id"), rs.getInt("item_template_id"), rs.getLong("count"), rs
							.getInt("enchant_level"), rs.getString("location"), rs.getInt("location_slot"), rs
							.getInt("custom_type1"), rs.getInt("custom_type2"), rs.getInt("mana_left"),
							expireAt == null ? -1 : expireAt.getTime()));
				}
				return items;
			}
		}
		finally
		{
			release(con, used);
		}
	}

	/** @return the worn items of the player */
	public List<PaperdollEntry> loadPaperdoll(int playerId) throws SQLException
	{
		Connection con = L2DatabaseFactory.getInstance().getConnection();
		try (PreparedStatement statement = con.prepareStatement(SELECT_PAPERDOLL))
		{
			statement.setInt(1, playerId);
			try (ResultSet rs = statement.executeQuery())
			{
				List<PaperdollEntry> entries = new ArrayList<PaperdollEntry>();
				while (rs.next())
					entries.add(new PaperdollEntry(rs.getInt("id"), rs.getInt("item_template_id"), rs
							.getInt("location_slot"), rs.getInt("enchant_level")));
				return entries;
			}
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}

	/**
	 * Stores a new item.
	 *
	 * @param con a connection to use, or null
	 * @param ownerKey id of the owner: a player, a clan, or the control item of a pet, as the location says
	 */
	public void insertItem(Connection con, ItemRow item, int ownerKey) throws SQLException
	{
		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try (PreparedStatement statement = used.prepareStatement(INSERT_ITEM))
		{
			statement.setInt(1, item.id());
			statement.setInt(2, item.itemTemplateId());
			setOwner(statement, 3, item.location(), ownerKey);
			statement.setString(6, item.location());
			statement.setInt(7, item.locationSlot());
			statement.setLong(8, item.count());
			statement.setInt(9, item.enchantLevel());
			statement.setInt(10, item.customType1());
			statement.setInt(11, item.customType2());
			statement.setInt(12, item.manaLeft());
			setMoment(statement, 13, item.expireAtMillis());
			statement.executeUpdate();
		}
		finally
		{
			release(con, used);
		}
	}

	/**
	 * Writes the changeable values of a stored item.
	 *
	 * @param con a connection to use, or null
	 * @param ownerKey id of the owner: a player, a clan, or the control item of a pet, as the location says
	 */
	public void updateItem(Connection con, ItemRow item, int ownerKey) throws SQLException
	{
		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try (PreparedStatement statement = used.prepareStatement(UPDATE_ITEM))
		{
			setOwner(statement, 1, item.location(), ownerKey);
			statement.setLong(4, item.count());
			statement.setString(5, item.location());
			statement.setInt(6, item.locationSlot());
			statement.setInt(7, item.enchantLevel());
			statement.setInt(8, item.customType1());
			statement.setInt(9, item.customType2());
			statement.setInt(10, item.manaLeft());
			setMoment(statement, 11, item.expireAtMillis());
			statement.setInt(12, item.id());
			statement.executeUpdate();
		}
		finally
		{
			release(con, used);
		}
	}

	/** Deletes an item. Its attributes, its pet row, and what the pet carries go with it. */
	public void deleteItem(Connection con, int itemId) throws SQLException
	{
		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try (PreparedStatement statement = used.prepareStatement(DELETE_ITEM))
		{
			statement.setInt(1, itemId);
			statement.executeUpdate();
		}
		finally
		{
			release(con, used);
		}
	}

	// Attributes ------------------------------------------------------------------------------------------------------

	/**
	 * @param con a connection to use, or null
	 * @return the augmentation and the elemental attribute of the item, or null when the item has neither
	 */
	public ItemAttributes loadAttributes(Connection con, int itemId) throws SQLException
	{
		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try (PreparedStatement statement = used.prepareStatement(SELECT_ATTRIBUTES))
		{
			statement.setInt(1, itemId);
			try (ResultSet rs = statement.executeQuery())
			{
				if (!rs.next())
					return null;

				return new ItemAttributes(getInteger(rs, "augmentation_attributes"), getInteger(rs,
						"augmentation_skill_id"), getInteger(rs, "augmentation_skill_level"), getInteger(rs,
						"element_type"), getInteger(rs, "element_value"));
			}
		}
		finally
		{
			release(con, used);
		}
	}

	/**
	 * Stores the attributes of an item; an item that has none loses its row.
	 *
	 * @param con a connection to use, or null
	 */
	public void saveAttributes(Connection con, int itemId, ItemAttributes attributes) throws SQLException
	{
		Connection used = L2DatabaseFactory.getInstance().getConnection(con);
		try
		{
			if (attributes == null || attributes.isEmpty())
			{
				try (PreparedStatement statement = used.prepareStatement(DELETE_ATTRIBUTES))
				{
					statement.setInt(1, itemId);
					statement.executeUpdate();
				}
				return;
			}

			try (PreparedStatement statement = used.prepareStatement(UPSERT_ATTRIBUTES))
			{
				statement.setInt(1, itemId);
				setInteger(statement, 2, attributes.augmentationAttributes());
				setInteger(statement, 3, attributes.augmentationSkillId());
				setInteger(statement, 4, attributes.augmentationSkillLevel());
				setInteger(statement, 5, attributes.elementType());
				setInteger(statement, 6, attributes.elementValue());
				statement.executeUpdate();
			}
		}
		finally
		{
			release(con, used);
		}
	}

	// Pets ------------------------------------------------------------------------------------------------------------

	/** Deletes the pet of a control item and everything the pet carries. */
	public void deletePet(int controlItemId) throws SQLException
	{
		Connection con = L2DatabaseFactory.getInstance().getConnection();
		try (PreparedStatement statement = con.prepareStatement(DELETE_PET))
		{
			statement.setInt(1, controlItemId);
			statement.executeUpdate();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}

	// Items on the ground ---------------------------------------------------------------------------------------------

	/** @return every saved item that lies on the ground */
	public List<GroundItem> loadGroundItems() throws SQLException
	{
		Connection con = L2DatabaseFactory.getInstance().getConnection();
		try (PreparedStatement statement = con.prepareStatement(SELECT_GROUND_ITEMS);
				ResultSet rs = statement.executeQuery())
		{
			List<GroundItem> items = new ArrayList<GroundItem>();
			while (rs.next())
			{
				Timestamp droppedAt = rs.getTimestamp("dropped_at");
				items.add(new GroundItem(rs.getInt("id"), rs.getInt("item_template_id"), rs.getLong("count"), rs
						.getInt("enchant_level"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
						droppedAt == null ? 0 : droppedAt.getTime(), rs.getBoolean("is_protected"), rs
								.getBoolean("is_equipable")));
			}
			return items;
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}

	/**
	 * Gives the protected items a drop time, so that the auto-destroy timer takes them.
	 *
	 * @param nowMillis the drop time to give, epoch milliseconds
	 * @param includeEquipable true to recycle equipment too, false to recycle other items only
	 */
	public void recycleProtectedGroundItems(long nowMillis, boolean includeEquipable) throws SQLException
	{
		Connection con = L2DatabaseFactory.getInstance().getConnection();
		try (PreparedStatement statement = con.prepareStatement(includeEquipable ? UNPROTECT_GROUND_ITEMS
				: UNPROTECT_GROUND_ITEMS_EXCEPT_EQUIPABLE))
		{
			statement.setTimestamp(1, new Timestamp(nowMillis));
			statement.executeUpdate();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}

	/** Deletes every saved item that lies on the ground. */
	public void deleteGroundItems() throws SQLException
	{
		Connection con = L2DatabaseFactory.getInstance().getConnection();
		try (PreparedStatement statement = con.prepareStatement(DELETE_GROUND_ITEMS))
		{
			statement.executeUpdate();
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}

	/**
	 * Rewrites the table of the items on the ground as a whole: the old rows go and the given ones are stored, in one
	 * transaction.
	 *
	 * @return true if the new rows were stored
	 */
	public boolean replaceGroundItems(List<GroundItem> items)
	{
		return WorldTransaction.run("Saving the items on the ground", () -> {
			Connection con = L2DatabaseFactory.getInstance().getConnection();
			try
			{
				try (PreparedStatement statement = con.prepareStatement(DELETE_GROUND_ITEMS))
				{
					statement.executeUpdate();
				}

				try (PreparedStatement statement = con.prepareStatement(INSERT_GROUND_ITEM))
				{
					for (GroundItem item : items)
					{
						statement.setInt(1, item.id());
						statement.setInt(2, item.itemTemplateId());
						statement.setLong(3, item.count());
						statement.setInt(4, item.enchantLevel());
						statement.setInt(5, item.x());
						statement.setInt(6, item.y());
						statement.setInt(7, item.z());
						setMoment(statement, 8, item.droppedAtMillis() > 0 ? item.droppedAtMillis() : -1);
						statement.setBoolean(9, item.isProtected());
						statement.setBoolean(10, item.isEquipable());
						statement.addBatch();
					}
					statement.executeBatch();
				}
			}
			catch (SQLException e)
			{
				throw new IllegalStateException(e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		});
	}

	// Helpers ---------------------------------------------------------------------------------------------------------

	private static boolean isPetLocation(String location)
	{
		return PET.equals(location) || PET_EQUIPPED.equals(location);
	}

	/** Binds the three owner columns from the position: the one that the location uses gets the key, the others NULL. */
	private static void setOwner(PreparedStatement statement, int first, String location, int ownerKey)
			throws SQLException
	{
		boolean clan = CLAN_WAREHOUSE.equals(location);
		boolean pet = isPetLocation(location);
		bindKey(statement, first, !clan && !pet, ownerKey);
		bindKey(statement, first + 1, clan, ownerKey);
		bindKey(statement, first + 2, pet, ownerKey);
	}

	private static void bindKey(PreparedStatement statement, int index, boolean used, int key) throws SQLException
	{
		if (used)
			statement.setInt(index, key);
		else
			statement.setNull(index, Types.INTEGER);
	}

	/** Binds a moment given as epoch milliseconds; zero or a negative value means "not set" and is stored as NULL. */
	private static void setMoment(PreparedStatement statement, int index, long millis) throws SQLException
	{
		if (millis > 0)
			statement.setTimestamp(index, new Timestamp(millis));
		else
			statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
	}

	private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException
	{
		if (value == null)
			statement.setNull(index, Types.INTEGER);
		else
			statement.setInt(index, value);
	}

	private static Integer getInteger(ResultSet rs, String column) throws SQLException
	{
		int value = rs.getInt(column);
		return rs.wasNull() ? null : Integer.valueOf(value);
	}

	/** Closes the connection when this class opened it, that is when the caller did not supply one. */
	private static void release(Connection supplied, Connection used)
	{
		if (supplied == null)
			L2DatabaseFactory.close(used);
	}
}
