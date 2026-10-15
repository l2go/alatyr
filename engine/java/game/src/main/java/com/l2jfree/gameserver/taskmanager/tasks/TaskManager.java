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

import static com.l2jfree.gameserver.taskmanager.tasks.TaskTypes.TYPE_SHEDULED;
import static com.l2jfree.gameserver.taskmanager.tasks.TaskTypes.TYPE_TIME;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.util.HandlerRegistry;

/**
 * @author Layane
 */
public final class TaskManager extends HandlerRegistry<String, TaskHandler>
{
	private static final class SingletonHolder
	{
		private static final TaskManager INSTANCE = new TaskManager();
	}
	
	public static TaskManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	final class ExecutedTask implements Runnable
	{
		private final long _id;
		private final TaskHandler _task;
		private final TaskTypes _type;
		private final String[] _params;
		
		private long _lastActivation;
		private ScheduledFuture<?> _scheduled;
		
		private ExecutedTask(ResultSet rset) throws SQLException
		{
			_id = rset.getLong("id");
			_task = get(rset.getString("task_name"));
			
			if (_task == null)
				throw new NullPointerException("Handler not found for '" + rset.getString("task_name") + "' task!");
			
			_type = TaskTypes.valueOf(rset.getString("schedule_type").toUpperCase());
			_params = new String[] { rset.getString("parameter1"), rset.getString("parameter2"), rset.getString("parameter3") };
			
			// NULL means the task never ran
			Timestamp lastRun = rset.getTimestamp("last_run_at");
			_lastActivation = lastRun == null ? 0L : lastRun.getTime();
			
			try
			{
				switch (_type)
				{
					case TYPE_STARTUP:
					{
						run();
						break;
					}
					case TYPE_SHEDULED:
					{
						_scheduled = ThreadPoolManager.getInstance().schedule(this, Long.parseLong(_params[0]));
						break;
					}
					case TYPE_FIXED_SHEDULED:
					{
						long delay = Long.parseLong(_params[0]);
						long interval = Long.parseLong(_params[1]);
						
						_scheduled = ThreadPoolManager.getInstance().scheduleAtFixedRate(this, delay, interval);
						break;
					}
					case TYPE_TIME:
					{
						long diff = DateFormat.getInstance().parse(_params[0]).getTime() - System.currentTimeMillis();
						
						if (diff >= 0)
							_scheduled = ThreadPoolManager.getInstance().schedule(this, diff);
						else
							throw new IllegalStateException();
						break;
					}
					case TYPE_SPECIAL:
					{
						_scheduled = _task.launchSpecial(this);
						break;
					}
					case TYPE_GLOBAL_TASK:
					{
						int days = Integer.parseInt(_params[0]);
						long interval = days * 86400000;
						String[] hour = _params[1].split(":");
						
						Calendar min = Calendar.getInstance();
						min.setTimeInMillis(_lastActivation);
						min.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour[0]));
						min.set(Calendar.MINUTE, Integer.parseInt(hour[1]));
						min.set(Calendar.SECOND, Integer.parseInt(hour[2]));
						
						while (min.getTimeInMillis() < System.currentTimeMillis())
						{
							min.add(Calendar.DAY_OF_YEAR, days);
						}
						
						long delay = min.getTimeInMillis() - System.currentTimeMillis();
						
						_scheduled = ThreadPoolManager.getInstance().scheduleAtFixedRate(this, delay, interval);
						break;
					}
					case TYPE_NONE:
					{
						break;
					}
				}
			}
			catch (Exception e)
			{
				_log.warn("Task {} failed", this, e);
			}
		}
		
		@Override
		public void run()
		{
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement =
						con.prepareStatement("UPDATE global_task SET last_run_at = ? WHERE id = ?");
				_lastActivation = System.currentTimeMillis();
				statement.setTimestamp(1, new Timestamp(_lastActivation));
				statement.setLong(2, _id);
				statement.executeUpdate();
				statement.close();
			}
			catch (SQLException e)
			{
				_log.warn("Task {} database update failed", this, e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			try
			{
				_task.onTimeElapsed(this, getParams());
			}
			catch (Exception e)
			{
				_log.warn("Task {} execution failed", this, e);
			}
			
			if (_type == TYPE_SHEDULED || _type == TYPE_TIME)
				stopTask();
		}
		
		private String[] getParams()
		{
			return _params;
		}
		
		private void stopTask()
		{
			_task.onDestroy(this);
			
			if (_scheduled != null)
				_scheduled.cancel(true);
		}
		
		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("[ID: ");
			sb.append(_id);
			sb.append(" - Task: ");
			sb.append(_task);
			sb.append(" - Type: ");
			sb.append(_type);
			sb.append(" - Parameters: ");
			sb.append(Arrays.toString(_params));
			sb.append(" - Last activation: ");
			sb.append(_lastActivation);
			sb.append(']');
			
			return sb.toString();
		}
	}
	
	private TaskManager()
	{
		registerTaskHandler(new TaskJython());
		registerTaskHandler(new TaskOlympiadSave());
		registerTaskHandler(new TaskRaidPointsReset());
		registerTaskHandler(new TaskRestart());
		registerTaskHandler(new TaskSevenSignsUpdate());
		registerTaskHandler(new TaskMailCleanUp());
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement("SELECT id, task_name, schedule_type, last_run_at, parameter1, parameter2, parameter3 FROM global_task");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
				new ExecutedTask(rset);
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("TaskManager: Loaded " + size() + " tasks.");
	}
	
	private void registerTaskHandler(TaskHandler taskHandler)
	{
		registerAll(taskHandler, taskHandler.getName());
	}
	
	@Override
	public String standardizeKey(String key)
	{
		return key.trim().toLowerCase();
	}
	
	static boolean addUniqueTask(String task, TaskTypes type, String param1, String param2, String param3)
	{
		return addUniqueTask(task, type, param1, param2, param3, 0);
	}
	
	static boolean addUniqueTask(String task, TaskTypes type, String param1, String param2, String param3,
			long lastActivation)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement("SELECT id FROM global_task WHERE task_name = ?");
			statement.setString(1, task);
			ResultSet rset = statement.executeQuery();
			
			if (!rset.next())
				addTask(task, type, param1, param2, param3, lastActivation);
			
			rset.close();
			statement.close();
			return true;
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return false;
	}
	
	static boolean addTask(String task, TaskTypes type, String param1, String param2, String param3)
	{
		return addTask(task, type, param1, param2, param3, 0);
	}
	
	static boolean
			addTask(String task, TaskTypes type, String param1, String param2, String param3, long lastActivation)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO global_task (task_name, schedule_type, last_run_at, parameter1, parameter2, parameter3) VALUES (?, ?, ?, ?, ?, ?)");
			statement.setString(1, task);
			statement.setString(2, type.toString());
			statement.setTimestamp(3, lastActivation == 0 ? null : new Timestamp(lastActivation));
			statement.setString(4, param1);
			statement.setString(5, param2);
			statement.setString(6, param3);
			statement.execute();
			statement.close();
			return true;
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return false;
	}
}
