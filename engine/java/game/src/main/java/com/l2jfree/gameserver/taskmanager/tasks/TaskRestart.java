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

import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.ShutdownMode;
import com.l2jfree.gameserver.taskmanager.tasks.TaskManager.ExecutedTask;

/**
 * @author Layane
 */
final class TaskRestart extends TaskHandler
{
	@Override
	void onTimeElapsed(ExecutedTask task, String[] params)
	{
		Shutdown.start("Auto-Restart", Integer.parseInt(params[2]), ShutdownMode.RESTART);
	}
}
