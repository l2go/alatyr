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

import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.taskmanager.tasks.TaskManager.ExecutedTask;

/**
 * Updates all data for the Seven Signs and Festival of Darkness engines, when time is elapsed.
 * 
 * @author Tempy
 */
final class TaskSevenSignsUpdate extends TaskHandler
{
	TaskSevenSignsUpdate()
	{
		TaskManager.addUniqueTask(getName(), TaskTypes.TYPE_FIXED_SHEDULED, "1800000", "1800000", "");
	}
	
	@Override
	void onTimeElapsed(ExecutedTask task, String[] params)
	{
		SevenSigns.getInstance().saveSevenSignsData(null, true);
		
		if (!SevenSigns.getInstance().isSealValidationPeriod())
			SevenSignsFestival.getInstance().saveFestivalData(false);
		
		_log.info("SevenSigns: Data updated successfully.");
	}
}
