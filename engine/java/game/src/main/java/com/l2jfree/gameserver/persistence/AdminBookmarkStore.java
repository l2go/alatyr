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
import java.util.ArrayList;
import java.util.List;

/** Teleport bookmarks shared by all GMs ({@code //bookmark}), {@code world.admin_teleport_bookmark}. */
public final class AdminBookmarkStore
{
	public record Bookmark(String name, int x, int y, int z)
	{
	}
	
	private AdminBookmarkStore()
	{
	}
	
	/** All bookmarks, the most recently saved last; none when the database cannot be read. */
	public static List<Bookmark> load()
	{
		return Transactions.withConnection("read the admin bookmarks", List.<Bookmark>of(), AdminBookmarkStore::load);
	}
	
	/** @return whether the bookmark was saved */
	public static boolean save(Bookmark bookmark)
	{
		return Transactions.withConnection("save the admin bookmark " + bookmark.name(), con -> save(con, bookmark));
	}
	
	/** @return whether the database could be written */
	public static boolean delete(String name)
	{
		return Transactions.withConnection("delete the admin bookmark " + name, con -> delete(con, name));
	}
	
	/** All bookmarks, the most recently saved last. */
	static List<Bookmark> load(Connection con) throws SQLException
	{
		List<Bookmark> bookmarks = new ArrayList<>();
		try (Statement statement = con.createStatement(); ResultSet rs =
				statement.executeQuery("SELECT name, x, y, z FROM admin_teleport_bookmark ORDER BY saved_order"))
		{
			while (rs.next())
				bookmarks.add(new Bookmark(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
		}
		return bookmarks;
	}
	
	/** Saves a bookmark; a bookmark with the same name is replaced and moves to the end. */
	static void save(Connection con, Bookmark bookmark) throws SQLException
	{
		Transactions.run(con, c -> {
			delete(c, bookmark.name());
			try (PreparedStatement insert =
					c.prepareStatement("INSERT INTO admin_teleport_bookmark (name, x, y, z) VALUES (?, ?, ?, ?)"))
			{
				insert.setString(1, bookmark.name());
				insert.setInt(2, bookmark.x());
				insert.setInt(3, bookmark.y());
				insert.setInt(4, bookmark.z());
				insert.executeUpdate();
			}
		});
	}
	
	static void delete(Connection con, String name) throws SQLException
	{
		try (PreparedStatement delete = con.prepareStatement("DELETE FROM admin_teleport_bookmark WHERE name = ?"))
		{
			delete.setString(1, name);
			delete.executeUpdate();
		}
	}
}
