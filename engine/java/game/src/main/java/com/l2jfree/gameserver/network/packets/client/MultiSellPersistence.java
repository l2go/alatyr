/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.network.packets.client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Writes every durable part of one multisell exchange on one JDBC connection. */
final class MultiSellPersistence
{
	private static final String DELETE_ITEM = "DELETE FROM item WHERE id=? AND owner_player_id=? AND count=?";
	private static final String UPDATE_ITEM_COUNT = "UPDATE item SET count=? WHERE id=? AND owner_player_id=? AND count=?";
	private static final String INSERT_ITEM =
			"INSERT INTO item (owner_player_id, id, item_template_id, count, enchant_level, location, location_slot, mana_left, expire_at) "
					+ "VALUES (?,?,?,?,?,'INVENTORY',0,?,?)";
	private static final String INSERT_ATTRIBUTES =
			"INSERT INTO item_attribute (item_id, augmentation_attributes, augmentation_skill_id, augmentation_skill_level, element_type, element_value) "
					+ "VALUES (?,?,?,?,?,?)";
	private static final String UPDATE_FAME = "UPDATE player SET fame=? WHERE id=?";
	private static final String UPDATE_CLAN_REPUTATION =
			"UPDATE clan SET reputation_score=? WHERE id=? AND reputation_score=?";
	private static final String UPDATE_CASTLE_TREASURY = "UPDATE castle SET treasury=? WHERE id=? AND treasury=?";

	interface Change
	{
		void write(Connection connection) throws SQLException;
	}

	private final List<Change> _changes = new ArrayList<Change>();

	void debit(final int objectId, final int ownerId, final long before, final long after)
	{
		if (after >= before)
			throw new IllegalArgumentException("Invalid item debit");
		changeCount(objectId, ownerId, before, after);
	}

	void changeCount(final int objectId, final int ownerId, final long before, final long after)
	{
		if (before <= 0 || after < 0 || after == before)
			throw new IllegalArgumentException("Invalid item count change");

		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				// The attributes and the pet of a deleted item go with its row
				String sql = after == 0 ? DELETE_ITEM : UPDATE_ITEM_COUNT;
				try (PreparedStatement statement = connection.prepareStatement(sql))
				{
					int offset = 0;
					if (after > 0)
						statement.setLong(++offset, after);
					statement.setInt(++offset, objectId);
					statement.setInt(++offset, ownerId);
					statement.setLong(++offset, before);
					if (statement.executeUpdate() != 1)
						throw new SQLException("Stale multisell ingredient: " + objectId);
				}
			}
		});
	}

	void insert(final int objectId, final int ownerId, final int itemId, final long count,
			final int enchantment)
	{
		insert(objectId, ownerId, itemId, count, enchantment, -1, 0);
	}

	void insert(final int objectId, final int ownerId, final int itemId, final long count,
			final int enchantment, final int mana, final long time)
	{
		if (count <= 0)
			throw new IllegalArgumentException("Invalid product count");
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM))
				{
					statement.setInt(1, ownerId);
					statement.setInt(2, objectId);
					statement.setInt(3, itemId);
					statement.setLong(4, count);
					statement.setInt(5, enchantment);
					statement.setInt(6, mana);
					// A time of 0 or -1 means that the item has no time limit
					if (time > 0)
						statement.setTimestamp(7, new Timestamp(time));
					else
						statement.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE);
					statement.executeUpdate();
				}
			}
		});
	}

	void insertAttributes(final int objectId, final int augmentation, final int skillId,
			final int skillLevel, final byte element, final int elementValue)
	{
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				try (PreparedStatement statement = connection.prepareStatement(INSERT_ATTRIBUTES))
				{
					// -1 means that the item has no such attribute, which the table stores as NULL
					statement.setInt(1, objectId);
					setUnlessNone(statement, 2, augmentation);
					setUnlessNone(statement, 3, skillId);
					setUnlessNone(statement, 4, skillLevel);
					setUnlessNone(statement, 5, element);
					setUnlessNone(statement, 6, elementValue);
					statement.executeUpdate();
				}
			}
		});
	}

	void adjustFame(final int playerId, final int before, final int after)
	{
		if (before == after)
			return;
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				// Fame is normally persisted by periodic character stores, so its row can lag memory.
				try (PreparedStatement statement = connection.prepareStatement(UPDATE_FAME))
				{
					statement.setInt(1, after);
					statement.setInt(2, playerId);
					if (statement.executeUpdate() != 1)
						throw new SQLException("Missing multisell character: " + playerId);
				}
			}
		});
	}

	void adjustClanReputation(final int clanId, final int before, final int after)
	{
		adjustBalance(UPDATE_CLAN_REPUTATION, "clan", clanId, before, after);
	}

	void adjustCastleTreasury(final int castleId, final long before, final long after)
	{
		adjustBalance(UPDATE_CASTLE_TREASURY, "castle", castleId, before, after);
	}

	private void adjustBalance(final String sql, final String table, final int key, final long before,
			final long after)
	{
		if (before == after)
			return;
		_changes.add(new Change()
		{
			@Override
			public void write(Connection connection) throws SQLException
			{
				try (PreparedStatement statement = connection.prepareStatement(sql))
				{
					statement.setLong(1, after);
					statement.setInt(2, key);
					statement.setLong(3, before);
					if (statement.executeUpdate() != 1)
						throw new SQLException("Stale multisell balance in " + table + ": " + key);
				}
			}
		});
	}

	void commit(Connection connection) throws SQLException
	{
		boolean originalAutoCommit = connection.getAutoCommit();
		if (!originalAutoCommit)
			throw new IllegalArgumentException("A dedicated auto-commit connection is required");
		connection.setAutoCommit(false);
		try
		{
			for (Change change : _changes)
				change.write(connection);
			connection.commit();
		}
		catch (SQLException | RuntimeException failure)
		{
			try
			{
				connection.rollback();
			}
			catch (SQLException rollbackFailure)
			{
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
	}

	private static void setUnlessNone(PreparedStatement statement, int index, int value) throws SQLException
	{
		if (value == -1)
			statement.setNull(index, Types.INTEGER);
		else
			statement.setInt(index, value);
	}
}
