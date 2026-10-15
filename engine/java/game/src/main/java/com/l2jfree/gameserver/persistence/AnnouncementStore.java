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

/** The announcements shown to every player who enters the world, {@code world.announcement}. */
public final class AnnouncementStore
{
	private AnnouncementStore()
	{
	}
	
	/** The announcements; none when the database cannot be read. */
	public static List<String> load()
	{
		return Transactions.withConnection("read the announcements", List.<String>of(), AnnouncementStore::load);
	}
	
	/** @return whether the announcements were saved */
	public static boolean replaceAll(List<String> announcements)
	{
		return Transactions.withConnection("save the announcements", con -> replaceAll(con, announcements));
	}
	
	static List<String> load(Connection con) throws SQLException
	{
		List<String> announcements = new ArrayList<>();
		try (Statement statement = con.createStatement();
				ResultSet rs = statement.executeQuery("SELECT message FROM announcement ORDER BY position"))
		{
			while (rs.next())
				announcements.add(rs.getString(1));
		}
		return announcements;
	}
	
	/** Replaces all announcements with {@code announcements}, in this order. */
	static void replaceAll(Connection con, List<String> announcements) throws SQLException
	{
		Transactions.run(con, c -> {
			try (Statement delete = c.createStatement();
					PreparedStatement insert =
							c.prepareStatement("INSERT INTO announcement (position, message) VALUES (?, ?)"))
			{
				delete.execute("DELETE FROM announcement");
				for (int i = 0; i < announcements.size(); i++)
				{
					insert.setInt(1, i);
					insert.setString(2, announcements.get(i));
					insert.addBatch();
				}
				insert.executeBatch();
			}
		});
	}
}
