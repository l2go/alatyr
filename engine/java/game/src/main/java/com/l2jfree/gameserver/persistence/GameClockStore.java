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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.OptionalLong;

/** The saved in-game time, {@code world.game_clock}. */
public final class GameClockStore
{
	private GameClockStore()
	{
	}
	
	public static OptionalLong load()
	{
		return Transactions.withConnection("read the game clock", OptionalLong.empty(), GameClockStore::load);
	}
	
	public static void save(long gameTimeMillis)
	{
		Transactions.withConnection("save the game clock", con -> save(con, gameTimeMillis));
	}
	
	static OptionalLong load(Connection con) throws SQLException
	{
		try (Statement statement = con.createStatement();
				ResultSet rs = statement.executeQuery("SELECT game_time_millis FROM game_clock WHERE id = 0"))
		{
			return rs.next() ? OptionalLong.of(rs.getLong(1)) : OptionalLong.empty();
		}
	}
	
	static void save(Connection con, long gameTimeMillis) throws SQLException
	{
		try (PreparedStatement statement = con.prepareStatement("INSERT INTO game_clock (id, game_time_millis) "
				+ "VALUES (0, ?) ON CONFLICT (id) DO UPDATE SET game_time_millis = EXCLUDED.game_time_millis"))
		{
			statement.setLong(1, gameTimeMillis);
			statement.executeUpdate();
		}
	}
}
