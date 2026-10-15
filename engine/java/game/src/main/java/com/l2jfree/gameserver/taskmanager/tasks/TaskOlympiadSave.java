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

import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.taskmanager.tasks.TaskManager.ExecutedTask;

/**
 * Updates all data of Olympiad nobles in db
 * 
 * @author godson
 */
final class TaskOlympiadSave extends TaskHandler
{
	TaskOlympiadSave()
	{
		TaskManager.addUniqueTask(getName(), TaskTypes.TYPE_FIXED_SHEDULED, "900000", "1800000", "");
	}
	
	@Override
	void onTimeElapsed(ExecutedTask task, String[] params)
	{
		if (Olympiad.getInstance().inCompPeriod())
		{
			Olympiad.getInstance().saveOlympiadStatus();
			_log.info("Olympiad System: Data updated.");
		}
	}
}
