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
package com.l2jfree.gameserver.taskmanager.tasks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.communitybbs.Manager.MailBBSManager;
import com.l2jfree.gameserver.taskmanager.tasks.TaskManager.ExecutedTask;

/**
 * @author Vital
 */
public class TaskMailCleanUp extends TaskHandler
{
	TaskMailCleanUp()
	{
		TaskManager.addUniqueTask(getName(), TaskTypes.TYPE_GLOBAL_TASK, "1", "13:00:00", "");
	}
	
	@Override
	void onTimeElapsed(ExecutedTask task, String[] params)
	{
		ArrayList<Integer> deleteLetterList = new ArrayList<Integer>();
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id FROM player_mail WHERE (folder = ? OR folder = ?) AND delete_at < ?");
			statement.setString(1, "inbox");
			statement.setString(2, "sentbox");
			statement.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
			ResultSet result = statement.executeQuery();
			while (result.next())
				deleteLetterList.add(result.getInt(1));
			result.close();
			statement.close();
			
			for (int letterId : deleteLetterList)
			{
				MailBBSManager.getInstance().deleteLetter(letterId);
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("Mail Clean Up Global Task: launched.");
	}
}
