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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The arena and fishing leaderboards, {@code world.leaderboard_entry}. */
public final class LeaderboardStore
{
	public enum Board
	{
		ARENA,
		FISHING;
		
		String code()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}
	
	/** Arena: kills and deaths. Fishing: fish caught and escaped. */
	public record Entry(int playerId, String playerName, int wins, int losses)
	{
	}
	
	private LeaderboardStore()
	{
	}
	
	/** The entries of the board; none when the database cannot be read. */
	public static List<Entry> load(Board board)
	{
		return Transactions.withConnection("read the " + board.code() + " leaderboard", List.<Entry>of(),
				con -> load(con, board));
	}
	
	/** @return whether the board was saved */
	public static boolean replace(Board board, List<Entry> entries)
	{
		return Transactions.withConnection("save the " + board.code() + " leaderboard",
				con -> replace(con, board, entries));
	}
	
	static List<Entry> load(Connection con, Board board) throws SQLException
	{
		List<Entry> entries = new ArrayList<>();
		try (PreparedStatement statement = con.prepareStatement("SELECT player_id, player_name, wins, losses "
				+ "FROM leaderboard_entry WHERE board = ? ORDER BY position"))
		{
			statement.setString(1, board.code());
			try (ResultSet rs = statement.executeQuery())
			{
				while (rs.next())
					entries.add(new Entry(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
			}
		}
		return entries;
	}
	
	/** Replaces the board with {@code entries}, in this order; players deleted meanwhile are left out. */
	static void replace(Connection con, Board board, List<Entry> entries) throws SQLException
	{
		Transactions.run(con, c -> {
			try (PreparedStatement delete = c.prepareStatement("DELETE FROM leaderboard_entry WHERE board = ?");
					PreparedStatement insert = c.prepareStatement("INSERT INTO leaderboard_entry "
							+ "(board, player_id, position, player_name, wins, losses) "
							+ "SELECT ?, id, ?, ?, ?, ? FROM player WHERE id = ?"))
			{
				delete.setString(1, board.code());
				delete.executeUpdate();
				for (int i = 0; i < entries.size(); i++)
				{
					Entry entry = entries.get(i);
					insert.setString(1, board.code());
					insert.setInt(2, i);
					insert.setString(3, entry.playerName());
					insert.setInt(4, entry.wins());
					insert.setInt(5, entry.losses());
					insert.setInt(6, entry.playerId());
					insert.addBatch();
				}
				insert.executeBatch();
			}
		});
	}
}
