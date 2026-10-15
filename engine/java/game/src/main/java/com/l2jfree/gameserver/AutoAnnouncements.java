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
package com.l2jfree.gameserver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;

public final class AutoAnnouncements
{
	private static final Logger _log = LoggerFactory.getLogger(Announcements.class);
	
	public static AutoAnnouncements getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final List<AutoAnnouncer> _announcers = new ArrayList<AutoAnnouncer>();
	
	private AutoAnnouncements()
	{
		restore();
	}
	
	public void reload()
	{
		for (AutoAnnouncer exec : _announcers)
			exec.cancel();
		
		_announcers.clear();
		
		restore();
	}
	
	private void announce(String text)
	{
		Announcements.getInstance().announceToAll(text);
		
		_log.info("AutoAnnounce: " + text);
	}
	
	private void restore()
	{
		Connection conn = null;
		try
		{
			conn = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement =
					conn.prepareStatement("SELECT initial_delay_s, interval_s, repeat_count, message FROM auto_announcement ORDER BY id");
			ResultSet data = statement.executeQuery();
			
			while (data.next())
			{
				final long initial = data.getLong("initial_delay_s");
				final long delay = data.getLong("interval_s");
				final int repeat = data.getInt("repeat_count");
				final String[] memo = data.getString("message").split("\n");
				
				_announcers.add(new AutoAnnouncer(memo, repeat, initial, delay));
			}
			
			data.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("AutoAnnoucements: Failed to load announcements data.", e);
		}
		finally
		{
			L2DatabaseFactory.close(conn);
		}
		
		_log.info("AutoAnnoucements: Loaded " + _announcers.size() + " Auto Annoucement Data.");
	}
	
	private final class AutoAnnouncer implements Runnable
	{
		private final String[] memo;
		private final Future<?> task;
		
		private int repeat;
		
		private AutoAnnouncer(String[] memo, int repeat, long initial, long delay)
		{
			this.memo = memo;
			
			if (repeat > 0)
				this.repeat = repeat;
			else
				this.repeat = -1;
			
			task = ThreadPoolManager.getInstance().scheduleAtFixedRate(this, initial * 1000, delay * 1000);
		}
		
		private void cancel()
		{
			task.cancel(false);
		}
		
		@Override
		public void run()
		{
			for (String text : memo)
				announce(text);
			
			if (repeat > 0)
				repeat--;
			
			if (repeat == 0)
				cancel();
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AutoAnnouncements _instance = new AutoAnnouncements();
	}
}
